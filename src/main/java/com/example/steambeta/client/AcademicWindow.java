package com.example.steambeta.client;

import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class AcademicWindow {
    private final Stage stage;
    private final BorderPane root=new BorderPane();
    private final SessionScope scope=new SessionScope();
    private final ExecutorService workers=Executors.newFixedThreadPool(4,r->{var t=new Thread(r,"campus-http");t.setDaemon(true);return t;});
    private ApiClient api;
    private ListView<ApiClient.Subject> subjects;
    private VBox detail;
    private Label libraryStatus;
    private Label accessStatus;
    private Button refresh;
    public AcademicWindow(Stage stage) { this.stage=stage; }
    public void open() {
        stage.setTitle("Campus | Biblioteca académica");
        var scene=new Scene(root,1120,740);
        scene.getStylesheets().add(getClass().getResource("academic.css").toExternalForm());
        stage.setScene(scene);stage.setMinWidth(900);stage.setMinHeight(620);
        stage.setOnCloseRequest(event->{scope.reset();if(api!=null){var old=api;workers.submit(()->{try{old.logout();}catch(Exception ignored){old.clear();}});}workers.shutdown();});
        access("");stage.show();
    }
    private Label label(String text,String style) { var l=new Label(text);l.getStyleClass().add(style);l.setWrapText(true);return l; }
    private Button button(String text,String style) { var b=new Button(text);b.getStyleClass().add(style);return b; }
    private VBox stack(Node... nodes) { var box=new VBox(16,nodes);box.setPadding(new Insets(30));return box; }
    private void access(String message) {
        scope.reset();api=null;
        if(subjects!=null){subjects.getItems().clear();subjects.getSelectionModel().clearSelection();}
        if(detail!=null)detail.getChildren().clear();
        root.setTop(null);root.setLeft(null);root.setBottom(null);
        var intro=stack(label("Campus","brand"),label("Tu recorrido\nacadémico","display"),
                label("Consulta tus materias, revisa tus actividades y sigue tu avance.","muted"));
        intro.getStyleClass().add("intro");intro.setPrefWidth(370);intro.setAlignment(Pos.CENTER_LEFT);
        var login=new TextField();login.setId("loginIdentifier");login.setPromptText("Identificador de acceso");login.setAccessibleText("Identificador de acceso");
        var password=new PasswordField();password.setId("loginPassword");password.setPromptText("Contraseña");password.setAccessibleText("Contraseña");
        var enter=button("Entrar","primary");enter.setId("loginButton");enter.setDefaultButton(true);enter.setMaxWidth(Double.MAX_VALUE);
        var busy=new ProgressIndicator();busy.setPrefSize(24,24);busy.setVisible(false);busy.setManaged(false);
        accessStatus=label(message,"error");accessStatus.setId("accessStatus");
        var form=stack(label("Acceso","title"),label("Usa tu cuenta académica para continuar.","muted"),
                label("Identificador de acceso","field-label"),login,label("Contraseña","field-label"),password,enter,busy,accessStatus);
        form.setMaxWidth(420);form.setAlignment(Pos.CENTER_LEFT);
        var center=new HBox(intro,new StackPane(form));HBox.setHgrow(center.getChildren().get(1),Priority.ALWAYS);root.setCenter(center);
        enter.setOnAction(event->{
            if(login.getText().isBlank() || password.getText().isEmpty()){accessStatus.setText("Escribe tu identificador y contraseña.");return;}
            long epoch=scope.reset(); final ApiClient attempt;
            try {attempt=new ApiClient();}catch(Exception error){accessStatus.setText("Revisa la dirección configurada del backend.");return;}
            String identifier=login.getText(),secret=password.getText();password.clear();
            enter.setDisable(true);login.setDisable(true);password.setDisable(true);busy.setManaged(true);busy.setVisible(true);accessStatus.setText("");
            work(()->attempt.login(identifier,secret),identity->{
                if(!scope.current(epoch)){attempt.clear();return;}
                api=attempt;library(identity);loadLibrary();
            },error->{
                if(!scope.current(epoch))return;
                attempt.clear();enter.setDisable(false);login.setDisable(false);password.setDisable(false);busy.setVisible(false);busy.setManaged(false);
                accessStatus.setText(error instanceof ApiClient.Failure failure && failure.status()==401
                        ?"Identificador o contraseña incorrectos.":"No se pudo iniciar sesión. Comprueba el backend y vuelve a intentarlo.");
                password.requestFocus();
            });
        });
        Platform.runLater(login::requestFocus);
    }
    private void library(ApiClient.Identity identity) {
        var logout=button("Cerrar sesión","secondary");logout.setId("logoutButton");
        logout.setOnAction(event->{
            var previous=api;access("Sesión cerrada.");long epoch=scope.generation();
            work(()->{previous.logout();return true;},ignored->{},error->{if(scope.current(epoch))accessStatus.setText("Sesión local cerrada. No se pudo confirmar la salida en el servidor.");});
        });
        var spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        var top=new HBox(20,label("Campus","brand"),label(identity.alias()+"\n"+identity.universityName(),"identity"),spacer,logout);
        top.setAlignment(Pos.CENTER_LEFT);top.setPadding(new Insets(20,26,20,26));top.getStyleClass().add("topbar");root.setTop(top);
        subjects=new ListView<>();subjects.setId("subjectList");subjects.setAccessibleText("Mis materias");
        subjects.setCellFactory(list->new ListCell<>() {
            @Override protected void updateItem(ApiClient.Subject subject,boolean empty) {
                super.updateItem(subject,empty);setText(null);
                if(empty||subject==null){setGraphic(null);return;}
                var box=new VBox(6,label(subject.code(),"subject-code"),label(subject.name(),"subject-name"),
                        label(subject.termCode()+" / "+subject.sectionCode(),"muted"));box.setPadding(new Insets(12,8,12,8));
                setGraphic(box);setAccessibleText(subject.code()+" "+subject.name());
            }
        });
        subjects.getSelectionModel().selectedItemProperty().addListener((observable,old,selected)->{if(selected!=null)loadDetail(selected);});
        libraryStatus=label("Cargando materias…","muted");libraryStatus.setId("libraryStatus");
        refresh=button("Actualizar materias","secondary");refresh.setOnAction(event->loadLibrary());
        var side=stack(label("Mis materias","section-title"),subjects,libraryStatus,refresh);side.setPadding(new Insets(22,18,22,18));
        side.setPrefWidth(300);side.getStyleClass().add("sidebar");VBox.setVgrow(subjects,Priority.ALWAYS);root.setLeft(side);
        detail=stack(label("Selecciona una materia","title"));detail.setId("subjectDetail");
        var scroll=new ScrollPane(detail);scroll.setFitToWidth(true);root.setCenter(scroll);
    }
    private void loadLibrary() {
        long epoch=scope.generation();var current=api;
        // Retire any in-flight detail when refreshing the library.
        scope.select();subjects.setDisable(true);refresh.setDisable(true);libraryStatus.setText("Cargando materias…");
        work(current::library,rows->{
            if(!scope.current(epoch))return;
            subjects.getSelectionModel().clearSelection();subjects.getItems().setAll(rows);subjects.setDisable(false);refresh.setDisable(false);
            libraryStatus.setText(rows.isEmpty()?"Aún no tienes materias matriculadas.":rows.size()+" materias matriculadas");
            detail.getChildren().setAll(label(rows.isEmpty()?"Tu biblioteca está vacía":"Selecciona una materia","title"));
            if(!rows.isEmpty())subjects.getSelectionModel().selectFirst();
        },error->{if(!scope.current(epoch))return;if(expired(error))return;subjects.setDisable(false);refresh.setDisable(false);libraryStatus.setText("No se pudo cargar la biblioteca. Pulsa Actualizar materias.");});
    }
    private void loadDetail(ApiClient.Subject subject) {
        long epoch=scope.generation(),request=scope.select();var current=api;
        detail.getChildren().setAll(label("Cargando materia…","title"));
        work(()->current.detail(subject.offeringId()),data->{
            if(!scope.current(epoch,request))return;
            var summary=data.summary();
            String percent=summary.progressPercent()==null?"Sin requisitos obligatorios":summary.progressPercent().stripTrailingZeros().toPlainString()+"%";
            var bar=new ProgressBar(summary.progressPercent()==null?0:summary.progressPercent().doubleValue()/100);bar.setMaxWidth(Double.MAX_VALUE);bar.setAccessibleText("Progreso: "+percent);
            detail.getChildren().setAll(label(summary.code(),"subject-code"),label(summary.name(),"title"),
                    label("Período "+summary.termCode()+"   /   Sección "+summary.sectionCode(),"muted"),
                    label("Tu progreso","section-title"),label(percent,"progress-number"),bar,
                    label(summary.completedCount()+" de "+summary.requiredCount()+" actividades obligatorias completadas","muted"),
                    label("Actividades","section-title"));
            if(summary.progressPercent()==null)detail.getChildren().add(label("Esta edición no tiene actividades obligatorias; el progreso no se calcula.","muted"));
            if(data.activities().isEmpty())detail.getChildren().add(label("No hay actividades publicadas.","muted"));
            for(var activity:data.activities()) {
                var row=stack(label((activity.completed()?"✓  ":"○  ")+activity.name(),"activity-name"),
                        label((activity.required()?"Obligatoria":"Opcional")+" / "+(activity.completed()?"Completada":"Pendiente"),"muted"));
                row.setPadding(new Insets(14,18,14,18));row.getStyleClass().add("activity");detail.getChildren().add(row);
            }
        },error->{if(!scope.current(epoch,request))return;if(expired(error))return;detail.getChildren().setAll(label("No se pudo cargar esta materia. Selecciona otra o actualiza la biblioteca.","error"));});
    }
    private boolean expired(Throwable error) {
        if(error instanceof ApiClient.Failure failure && failure.status()==401){var old=api;access("La sesión venció. Vuelve a entrar.");if(old!=null)old.clear();return true;}return false;
    }
    private <T> void work(Callable<T> operation,Consumer<T> success,Consumer<Throwable> failure) {
        workers.submit(()->{try{T result=operation.call();Platform.runLater(()->success.accept(result));}
            catch(Exception error){Platform.runLater(()->failure.accept(error));}});
    }
}
