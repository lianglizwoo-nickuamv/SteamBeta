package com.example.steambeta.backend.demo;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Explicit synthetic dataset. Insert only; a mismatch aborts the caller's entire transaction. */
public final class DemoDataset {
    private final Connection connection;
    public DemoDataset(Connection connection) { this.connection=connection; }
    public static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("replica-demo-library-v1:"+name).getBytes(StandardCharsets.UTF_8));
    }
    private static LinkedHashMap<String,Object> fields(Object... pairs) {
        var result=new LinkedHashMap<String,Object>();
        for(int i=0;i<pairs.length;i+=2) result.put((String)pairs[i],pairs[i+1]);
        return result;
    }
    private void execute(String sql,Object... args) throws SQLException {
        try(var s=connection.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++) s.setObject(i+1,args[i]);
            s.execute();
        }
    }
    private void ensure(String table,LinkedHashMap<String,Object> key,LinkedHashMap<String,Object> data) throws SQLException {
        var all=new LinkedHashMap<>(key); all.putAll(data);
        String where=String.join(" and ",key.keySet().stream().map(k->k+"=?").toList());
        try(var s=connection.prepareStatement("select "+String.join(",",all.keySet())+" from replica."+table+" where "+where)) {
            int i=1; for(var value:key.values()) s.setObject(i++,value);
            try(var rs=s.executeQuery()) {
                if(rs.next()) {
                    for(var entry:all.entrySet()) if(!Objects.equals(entry.getValue(),rs.getObject(entry.getKey())))
                        throw new IllegalStateException("Demo conflict in "+table+"; transaction cancelled");
                    return;
                }
            }
        }
        execute("insert into replica."+table+" ("+String.join(",",all.keySet())+") values ("+
                String.join(",",Collections.nCopies(all.size(),"?"))+")",all.values().toArray());
    }
    public void load(PasswordEncoder encoder,Map<String,String> passwords) throws SQLException {
        for(var university:List.of("aurora","horizonte")) ensure("university",fields("id",id(university)),fields(
                "code","DEMO-"+university.toUpperCase(Locale.ROOT),"name","Universidad Ficticia "+(university.equals("aurora")?"Aurora":"Horizonte")));
        for(var student:List.of("alba","bruno","celia")) {
            var university=id(student.equals("celia")?"horizonte":"aurora");
            ensure("student",fields("id",id(student)),fields("university_id",university,"alias",Character.toUpperCase(student.charAt(0))+student.substring(1)+" Demo"));
            ensure("wallet",fields("university_id",university,"student_id",id(student)),fields("balance",0L));
            account(student,university,encoder,Objects.requireNonNull(passwords.get(student)));
        }
        subject("programacion","aurora","PRO-101","Programación y algoritmos",2,1,"alba");
        subject("matematica","aurora","MAT-102","Matemática discreta",1,1,"alba");
        subject("seminario","aurora","SEM-103","Seminario de investigación",0,0,"alba");
        subject("redes","aurora","RED-201","Redes y comunicación",2,0,"bruno");
        subject("diseno","horizonte","DIS-101","Diseño de sistemas",1,1,"celia");
    }
    private void account(String student,UUID university,PasswordEncoder encoder,String password) throws SQLException {
        if(password.length()<12) throw new IllegalArgumentException("Demo passwords require at least 12 characters");
        var accountId=id("account:"+student);
        try(var s=connection.prepareStatement("select id,university_id,student_id,password_hash,enabled from replica.access_account where login_identifier=?")) {
            s.setString(1,"demo."+student);
            try(var rs=s.executeQuery()) {
                if(rs.next()) {
                    if(!accountId.equals(rs.getObject(1)) || !university.equals(rs.getObject(2))
                            || !id(student).equals(rs.getObject(3)) || !encoder.matches(password,rs.getString(4)) || !rs.getBoolean(5))
                        throw new IllegalStateException("Demo account conflicts with existing data");
                    return;
                }
            }
        }
        ensure("access_account",fields("id",accountId),fields("login_identifier","demo."+student,
                "university_id",university,"student_id",id(student),"enabled",true,"password_hash",encoder.encode(password)));
    }
    private void subject(String name,String institution,String code,String title,int required,int completed,String student) throws SQLException {
        var university=id(institution); var offering=id("offering:"+name); var subject=id("subject:"+name);
        ensure("subject",fields("id",subject),fields("university_id",university,"code",code,"name",title));
        ensure("offering",fields("id",offering),fields("university_id",university,"subject_id",subject,"term_code","2026-II","section_code","Demo A"));
        for(int i=1;i<=required;i++) ensure("activity",fields("id",id(name+":required:"+i)),fields(
                "university_id",university,"offering_id",offering,"code","R"+i,
                "name",i==1?"Fundamentos y práctica guiada":"Proyecto de aplicación","required",true));
        ensure("activity",fields("id",id(name+":optional")),fields("university_id",university,"offering_id",offering,
                "code","O1","name","Lectura y exploración complementaria","required",false));
        // Required activities are created/checked before opening; never update a frozen plan.
        execute("select set_config('replica.university_id',?,true)",university.toString());
        execute("select replica.open_enrollment(?)",offering);
        ensure("enrollment",fields("university_id",university,"student_id",id(student),"offering_id",offering),fields());
        for(int i=1;i<=completed;i++) ensure("activity_completion",fields("university_id",university,"student_id",id(student),
                "offering_id",offering,"activity_id",id(name+":required:"+i)),fields());
    }
}
