package com.example.steambeta.client;

import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javafx.beans.property.SimpleObjectProperty;

public final class AcademicWindow {
    private final Stage stage;
    private final BorderPane root=new BorderPane();
    private final SessionScope scope=new SessionScope();
    private final ExecutorService workers=Executors.newFixedThreadPool(4,r->{var t=new Thread(r,"campus-http");t.setDaemon(true);return t;});
    private ApiClient api;
    private ApiClient.Identity identity;
    private boolean closing;
    private ListView<ApiClient.Subject> subjects;
    private VBox detail;
    private Label libraryStatus;
    private Label accessStatus;
    private Button refresh;
    public AcademicWindow(Stage stage) { this.stage=stage; }
    public void open() {
        stage.setTitle("Campus | Comunidad universitaria");
        var scene=new Scene(root,1120,740);
        scene.getStylesheets().add(getClass().getResource("academic.css").toExternalForm());
        stage.setScene(scene);stage.setMinWidth(900);stage.setMinHeight(620);
        stage.setOnCloseRequest(event->{event.consume();closeWindow();});
        access("");stage.show();
    }
    private Label label(String text,String style) { var l=new Label(text);l.getStyleClass().add(style);l.setWrapText(true);return l; }
    private Button button(String text,String style) { var b=new Button(text);b.getStyleClass().add(style);return b; }
    private VBox stack(Node... nodes) { var box=new VBox(16,nodes);box.setPadding(new Insets(30));return box; }
    private void access(String message) {
        access(message, "");
    }
    private void access(String message, String presetLogin) {
        scope.reset();api=null;identity=null;
        if(subjects!=null){subjects.getItems().clear();subjects.getSelectionModel().clearSelection();}
        if(detail!=null)detail.getChildren().clear();
        root.setTop(null);root.setLeft(null);root.setBottom(null);
        var intro=stack(label("Campus","brand"),label("Tu comunidad\nuniversitaria","display"),
                label("Comparte dudas y conversa con tu grupo. No necesitas una matrícula verificada.","muted"));
        intro.getStyleClass().add("intro");intro.setPrefWidth(370);intro.setAlignment(Pos.CENTER_LEFT);
        var login=new TextField();login.setId("loginIdentifier");login.setPromptText("Identificador de acceso");login.setAccessibleText("Identificador de acceso");
        login.setText(presetLogin);
        var password=new PasswordField();password.setId("loginPassword");password.setPromptText("Contraseña");password.setAccessibleText("Contraseña");
        var enter=button("Entrar","primary");enter.setId("loginButton");enter.setDefaultButton(true);enter.setMaxWidth(Double.MAX_VALUE);
        var createAccount=button("Crear una cuenta","secondary");createAccount.setId("openRegistration");
        createAccount.setOnAction(event->registration());
        var busy=new ProgressIndicator();busy.setPrefSize(24,24);busy.setVisible(false);busy.setManaged(false);
        accessStatus=label(message,"error");accessStatus.setId("accessStatus");
        var form=stack(label("Acceso","title"),label("Entra con tu cuenta de Campus.","muted"),
                label("Identificador de acceso","field-label"),login,label("Contraseña","field-label"),password,enter,createAccount,busy,accessStatus);
        form.setMaxWidth(420);form.setAlignment(Pos.CENTER_LEFT);
        var center=new HBox(intro,new StackPane(form));HBox.setHgrow(center.getChildren().get(1),Priority.ALWAYS);root.setCenter(center);
        enter.setOnAction(event->{
            if(login.getText().isBlank() || password.getText().isEmpty()){accessStatus.setText("Escribe tu identificador y contraseña.");return;}
            long epoch=scope.reset(); final ApiClient attempt;
            try {attempt=new ApiClient();}catch(Exception error){accessStatus.setText("Revisa la dirección configurada del backend.");return;}
            String identifier=login.getText(),secret=password.getText();password.clear();
            enter.setDisable(true);createAccount.setDisable(true);login.setDisable(true);password.setDisable(true);busy.setManaged(true);busy.setVisible(true);accessStatus.setText("");
            work(()->attempt.login(identifier,secret),signedIdentity->{
                if(!scope.current(epoch)){attempt.clear();return;}
                api=attempt;identity=signedIdentity;home();
            },error->{
                if(!scope.current(epoch))return;
                attempt.clear();enter.setDisable(false);createAccount.setDisable(false);login.setDisable(false);password.setDisable(false);busy.setVisible(false);busy.setManaged(false);
                accessStatus.setText(error instanceof ApiClient.Failure failure && failure.status()==401
                        ?"Identificador o contraseña incorrectos.":"No se pudo iniciar sesión. Comprueba el backend y vuelve a intentarlo.");
                password.requestFocus();
            });
        });
        Platform.runLater(login::requestFocus);
    }
    private void library(ApiClient.Identity identity) {
        root.setBottom(null);
        var logout=button("Cerrar sesión","secondary");logout.setId("logoutButton");
        logout.setOnAction(event->logout());
        var communityHome=button("Mis comunidades","secondary");
        communityHome.setOnAction(event->home());
        var spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        var top=new HBox(20,label("Campus","brand"),label(identity.alias()+"\n"+identity.universityName(),"identity"),spacer,communityHome,logout);
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
    private void registration() {
        long epoch = scope.reset();
        root.setTop(null);
        root.setLeft(null);
        root.setBottom(null);
        var alias = new TextField();
        alias.setId("registerAlias");
        alias.setPromptText("Alias público (2 a 60 caracteres)");
        var login = new TextField();
        login.setId("registerLogin");
        login.setPromptText("Identificador de acceso");
        var password = new PasswordField();
        password.setId("registerPassword");
        password.setPromptText("Contraseña (mínimo 12 caracteres)");
        var confirmation = new PasswordField();
        confirmation.setId("registerConfirmation");
        confirmation.setPromptText("Repite la contraseña");
        var university = new TextField();
        university.setPromptText("Universidad declarada (opcional)");
        var career = new TextField();
        career.setPromptText("Carrera declarada (opcional)");
        var status = label("", "error");
        status.setId("registrationStatus");
        var submit = button("Crear cuenta", "primary");
        submit.setId("registerButton");
        var back = button("Volver al acceso", "secondary");
        back.setOnAction(event -> access(""));
        var fields = new VBox(10, label("Alias público", "field-label"), alias,
                label("Identificador de acceso", "field-label"), login,
                label("Contraseña", "field-label"), password, confirmation, university, career);
        var form = stack(label("Crear cuenta", "title"),
                label("La universidad y carrera son datos declarados, sin verificación institucional.", "muted"),
                fields, submit, back, status);
        form.setMaxWidth(540);
        var center = new StackPane(form);
        var scroll = new ScrollPane(center);
        scroll.setFitToWidth(true);
        root.setCenter(scroll);
        submit.setOnAction(event -> {
            if (alias.getText().strip().length() < 2 || login.getText().isBlank()
                    || password.getText().codePointCount(0, password.getText().length()) < 12
                    || password.getText().getBytes(StandardCharsets.UTF_8).length > 72) {
                status.setText("Escribe un alias, un identificador y una contraseña de 12 caracteres o más (máximo 72 bytes).");
                return;
            }
            if (!password.getText().equals(confirmation.getText())) {
                status.setText("Las contraseñas no coinciden.");
                return;
            }
            final ApiClient attempt;
            try {
                attempt = new ApiClient();
            } catch (Exception invalidUrl) {
                status.setText("Revisa la dirección configurada del backend.");
                return;
            }
            String name = login.getText();
            String secret = password.getText();
            String publicAlias = alias.getText();
            String declaredUniversity = university.getText();
            String declaredCareer = career.getText();
            fields.setDisable(true);
            submit.setDisable(true);
            back.setDisable(true);
            status.setText("Creando la cuenta…");
            work(() -> {
                try {
                    attempt.register(name, secret, publicAlias, declaredUniversity, declaredCareer);
                    return true;
                } finally {
                    attempt.clear();
                }
            }, done -> {
                if (scope.current(epoch)) access("Cuenta creada. Ya puedes iniciar sesión.", name);
            }, error -> {
                if (!scope.current(epoch)) return;
                fields.setDisable(false);
                submit.setDisable(false);
                back.setDisable(false);
                password.clear();
                confirmation.clear();
                if (error instanceof ApiClient.Failure failure && failure.status() == 409) {
                    status.setText("Ese identificador ya está ocupado. Si acabas de registrarte, vuelve al acceso.");
                } else {
                    status.setText(message(error) + " Si la cuenta se creó antes de perder la conexión, puedes intentar acceder.");
                }
            });
        });
    }

    private void accountHeader() {
        var logout = button("Cerrar sesión", "secondary");
        logout.setId("logoutButton");
        logout.setOnAction(event -> logout());
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var top = new HBox(16, label("Campus", "brand"), label(identity.alias(), "identity"), spacer);
        if (identity.studentId() != null) {
            var academic = button("Biblioteca académica", "secondary");
            academic.setOnAction(event -> {
                scope.reset();
                library(identity);
                loadLibrary();
            });
            top.getChildren().add(academic);
        }
        top.getChildren().add(logout);
        top.setPadding(new Insets(18, 24, 18, 24));
        top.setAlignment(Pos.CENTER_LEFT);
        top.getStyleClass().add("topbar");
        root.setTop(top);
        root.setLeft(null);
        root.setBottom(null);
    }

    private void home() {
        long epoch = scope.reset();
        var current = api;
        accountHeader();
        var list = new VBox(14);
        list.setId("communityList");
        var status = label("Cargando tus comunidades…", "muted");
        status.setId("communityStatus");
        var refreshCommunities = button("Actualizar comunidades", "secondary");
        refreshCommunities.setDisable(true);
        refreshCommunities.setOnAction(event -> home());
        var content = stack(label("Mis comunidades", "title"), status, refreshCommunities, list);
        var scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        root.setCenter(scroll);
        work(current::communities, communities -> {
            if (!scope.current(epoch)) return;
            refreshCommunities.setDisable(false);
            if (communities.isEmpty()) {
                status.setText("Tu cuenta está lista. Todavía no perteneces a una comunidad. "
                        + "El equipo del piloto puede incorporarte usando tu identificador de acceso; después pulsa Actualizar comunidades.");
                return;
            }
            status.setText("Elige una comunidad para leer y compartir con tu grupo.");
            for (var community : communities) {
                var enter = button("Entrar a la comunidad", "primary");
                enter.setOnAction(event -> openCommunity(community, ""));
                var card = stack(label(community.name(), "section-title"),
                        label(community.description(), "muted"), enter);
                card.getStyleClass().add("activity");
                list.getChildren().add(card);
            }
        }, error -> {
            if (!scope.current(epoch) || expired(error)) return;
            refreshCommunities.setDisable(false);
            status.setText(message(error));
        });
    }

    private void openCommunity(ApiClient.Community community, String notification) {
        long epoch = scope.reset();
        var current = api;
        accountHeader();
        var back = button("Mis comunidades", "secondary");
        back.setOnAction(event -> home());
        var refreshPosts = button("Ver más recientes", "secondary");
        refreshPosts.setOnAction(event -> openCommunity(community, ""));
        var feed = new VBox(14);
        feed.setId("postList");
        var status = label(notification.isEmpty() ? "Cargando publicaciones…" : notification, "muted");
        status.setId("postStatus");
        var more = button("Más antiguas", "secondary");
        more.setId("olderPosts");
        var heading = new HBox(12, back, refreshPosts);
        if ("MODERATOR".equals(community.role()) || "ADMIN".equals(community.role())) {
            var moderation = button("Reportes y moderación", "secondary");
            moderation.setId("moderationButton");
            moderation.setOnAction(event -> openModeration(community));
            heading.getChildren().add(moderation);
        }
        var content = stack(heading, label(community.name(), "title"),
                label(community.rules(), "muted"), status, feed, more);
        var scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        root.setCenter(scroll);

        var title = new TextField();
        title.setId("postTitle");
        title.setPromptText("Título de la duda (3 a 160 caracteres)");
        var body = new TextArea();
        body.setId("postBody");
        body.setPromptText("Explica tu duda (hasta 10 000 caracteres)");
        body.setWrapText(true);
        body.setPrefRowCount(3);
        var key = new SimpleObjectProperty<>(UUID.randomUUID());
        title.textProperty().addListener((observable, old, value) -> key.set(UUID.randomUUID()));
        body.textProperty().addListener((observable, old, value) -> key.set(UUID.randomUUID()));
        var publish = button("Publicar duda", "primary");
        publish.setId("publishButton");
        var publishStatus = label("", "error");
        publishStatus.setId("publishStatus");
        var composer = new VBox(8, title, body, new HBox(12, publish, publishStatus));
        composer.setPadding(new Insets(14, 30, 18, 30));
        composer.getStyleClass().add("topbar");
        root.setBottom(composer);
        publish.setOnAction(event -> {
            String requestedTitle = title.getText().strip();
            String requestedBody = body.getText().strip();
            int titleLength = requestedTitle.codePointCount(0, requestedTitle.length());
            if (titleLength < 3 || titleLength > 160 || requestedBody.isEmpty()
                    || requestedBody.codePointCount(0, requestedBody.length()) > 10000) {
                publishStatus.setText("Revisa el título (3–160 caracteres) y el contenido (1–10 000).");
                return;
            }
            UUID requestKey = key.get();
            title.setDisable(true);
            body.setDisable(true);
            publish.setDisable(true);
            publishStatus.setText("Publicando…");
            work(() -> current.createPost(community.id(), requestKey, requestedTitle, requestedBody), created -> {
                if (scope.current(epoch)) openCommunity(community, "Publicación guardada.");
            }, error -> {
                if (!scope.current(epoch) || expired(error)) return;
                title.setDisable(false);
                body.setDisable(false);
                publish.setDisable(false);
                publishStatus.setText(message(error) + " Puedes reintentar sin cambiar el texto.");
            });
        });
        loadPosts(community, feed, more, status, null, null, epoch);
    }

    private void loadPosts(ApiClient.Community community, VBox feed, Button more, Label status,
                           String beforeTime, String beforeId, long epoch) {
        var current = api;
        more.setDisable(true);
        work(() -> current.posts(community.id(), beforeTime, beforeId), page -> {
            if (!scope.current(epoch)) return;
            if (beforeTime == null) feed.getChildren().clear();
            if (page.posts().isEmpty() && beforeTime == null) {
                status.setText("Aún no hay publicaciones. Puedes compartir la primera duda.");
            } else {
                status.setText("Publicaciones de más recientes a más antiguas.");
            }
            var dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
            for (var post : page.posts()) feed.getChildren().add(postCard(community, post, epoch, dateFormat));
            more.setDisable(false);
            more.setVisible(page.nextBeforeTime() != null);
            more.setManaged(more.isVisible());
            more.setText("Más antiguas");
            more.setOnAction(event -> loadPosts(community, feed, more, status,
                    page.nextBeforeTime(), page.nextBeforeId(), epoch));
        }, error -> {
            if (!scope.current(epoch) || expired(error)) return;
            status.setText(message(error));
            more.setText("Reintentar carga");
            more.setDisable(false);
            more.setVisible(true);
            more.setManaged(true);
            more.setOnAction(event -> loadPosts(community, feed, more, status, beforeTime, beforeId, epoch));
        });
    }

    private VBox postCard(ApiClient.Community community, ApiClient.Post post, long epoch, DateTimeFormatter dateFormat) {
        var sessionApi = api;
        var card = new VBox(10);
        card.getStyleClass().add("activity");
        card.getChildren().addAll(label(post.title(), "section-title"),
                label(post.authorAlias() + " · " + dateFormat.format(Instant.parse(post.createdAt())), "muted"));
        if (post.body() != null && !post.body().isBlank()) card.getChildren().add(label(post.body(), "post-body"));
        if ("DELETED".equals(post.status())) card.getChildren().add(label("La publicación fue eliminada; las respuestas existentes se conservan.", "muted"));

        var actions = new HBox(8);
        if (post.authorAccountId().equals(sessionApi.accountId()) && "ACTIVE".equals(post.status())) {
            var edit = button("Editar", "secondary"); edit.setOnAction(e -> editPost(community, post, epoch)); actions.getChildren().add(edit);
            var delete = button("Eliminar", "secondary"); delete.setOnAction(e -> deletePost(community, post, epoch)); actions.getChildren().add(delete);
        } else if ("ACTIVE".equals(post.status())) {
            var report = button("Reportar", "secondary"); report.setOnAction(e -> reportContent(community, "POST", post.id())); actions.getChildren().add(report);
        }
        card.getChildren().add(actions);

        var comments = new VBox(8);
        comments.setId("comments-" + post.id());
        comments.getChildren().add(label("Cargando comentarios…", "muted"));
        card.getChildren().add(comments);
        work(() -> sessionApi.comments(community.id(), post.id()), rows -> {
            if (!scope.current(epoch)) return;
            comments.getChildren().clear();
            comments.getChildren().add(label("Comentarios", "field-label"));
            for (var comment : rows) comments.getChildren().add(commentCard(community, comment, epoch));
            if ("ACTIVE".equals(post.status())) {
                var input = new TextField(); input.setPromptText("Escribe un comentario (hasta 5 000 caracteres)");
                var submit = button("Comentar", "primary"); var state = label("", "error");
                submit.setOnAction(e -> {
                    String body = input.getText().strip();
                    if (body.isEmpty() || body.codePointCount(0, body.length()) > 5000) { state.setText("El comentario debe tener entre 1 y 5 000 caracteres."); return; }
                    submit.setDisable(true); state.setText("Enviando…");
                    work(() -> sessionApi.comment(community.id(), post.id(), body), created -> {
                        if (!scope.current(epoch)) return;
                        if (scope.current(epoch)) openCommunity(community, "Comentario publicado.");
                    }, error -> { if (scope.current(epoch) && !expired(error)) { submit.setDisable(false); state.setText(message(error)); } });
                });
                comments.getChildren().addAll(input, new HBox(8, submit, state));
            }
        }, error -> { if (scope.current(epoch) && !expired(error)) comments.getChildren().setAll(label(message(error), "error")); });
        return card;
    }

    private void loadComments(ApiClient.Community community, ApiClient.Post post, VBox container, long epoch) {
        var sessionApi = api;
        work(() -> sessionApi.comments(community.id(), post.id()), rows -> {
            if (!scope.current(epoch)) return;
            container.getChildren().clear(); container.getChildren().add(label("Comentarios", "field-label"));
            for (var comment : rows) container.getChildren().add(commentCard(community, comment, epoch));
        }, error -> { if (scope.current(epoch) && !expired(error)) container.getChildren().setAll(label(message(error), "error")); });
    }

    private VBox commentCard(ApiClient.Community community, ApiClient.Comment comment, long epoch) {
        var sessionApi = api;
        var card = new VBox(6); card.getStyleClass().add("comment-card");
        card.getChildren().add(label(comment.authorAlias(), "muted"));
        card.getChildren().add(label(comment.body() == null ? "Comentario eliminado u ocultado." : comment.body(), "post-body"));
        if ("ACTIVE".equals(comment.status()) && comment.authorAccountId().equals(sessionApi.accountId())) {
            var edit = button("Editar comentario", "secondary");
            edit.setOnAction(e -> {
                var value = editText("Editar comentario", "Actualiza tu comentario", comment.body());
                if (value != null) work(() -> { sessionApi.editComment(community.id(), comment.id(), value); return true; }, ok -> reloadCommunity(community, epoch), err -> { if (!expired(err)) messageDialog(message(err)); });
            });
            var delete = button("Eliminar comentario", "secondary"); delete.setOnAction(e -> {
                if (confirm("Eliminar comentario", "Se borrará el texto y quedará el marcador.")) work(() -> { sessionApi.deleteComment(community.id(), comment.id()); return true; }, ok -> reloadCommunity(community, epoch), err -> { if (!expired(err)) messageDialog(message(err)); });
            });
            card.getChildren().addAll(edit, delete);
        } else if ("ACTIVE".equals(comment.status())) {
            var report = button("Reportar comentario", "secondary"); report.setOnAction(e -> reportContent(community, "COMMENT", comment.id())); card.getChildren().add(report);
        }
        return card;
    }

    private void editPost(ApiClient.Community community, ApiClient.Post post, long epoch) {
        var sessionApi = api;
        var title = new TextInputDialog(post.title()); title.setTitle("Editar publicación"); title.setHeaderText("Nuevo título");
        title.showAndWait().ifPresent(newTitle -> {
            String newBody = editText("Editar publicación", "Nuevo contenido", post.body());
            if (newBody != null) work(() -> { sessionApi.editPost(community.id(), post.id(), newTitle, newBody); return true; }, ok -> reloadCommunity(community, epoch), err -> { if (!expired(err)) messageDialog(message(err)); });
        });
    }

    private void deletePost(ApiClient.Community community, ApiClient.Post post, long epoch) {
        var sessionApi = api;
        if (confirm("Eliminar publicación", "Se borrará su texto. Los comentarios existentes quedarán bajo un marcador y no admitirán nuevas respuestas."))
            work(() -> { sessionApi.deletePost(community.id(), post.id()); return true; }, ok -> reloadCommunity(community, epoch), err -> { if (!expired(err)) messageDialog(message(err)); });
    }

    private void reportContent(ApiClient.Community community, String type, String id) {
        var sessionApi = api;
        var dialog = new TextInputDialog(); dialog.setTitle("Reportar contenido"); dialog.setHeaderText("Explica brevemente el motivo (3–1 000 caracteres)");
        dialog.showAndWait().ifPresent(reason -> work(() -> { sessionApi.report(community.id(), type, id, reason); return true; }, ok -> messageDialog("Reporte enviado al equipo moderador."), err -> { if (!expired(err)) messageDialog(message(err)); }));
    }

    private void openModeration(ApiClient.Community community) {
        long epoch = scope.reset(); var current = api; accountHeader();
        var back = button("Volver a la comunidad", "secondary"); back.setOnAction(e -> openCommunity(community, ""));
        var list = new VBox(12); var status = label("Cargando reportes…", "muted");
        root.setBottom(null); root.setCenter(new ScrollPane(stack(back, label("Reportes pendientes", "title"), status, list)));
        work(() -> current.reports(community.id()), reports -> {
            if (!scope.current(epoch)) return;
            list.getChildren().clear();
            if (reports.isEmpty()) { status.setText("No hay reportes pendientes."); return; }
            status.setText("Los reportes y las acciones quedan registrados.");
            for (var report : reports) {
                var card = stack(label("Reportado por " + report.reporterAlias(), "section-title"),
                        label(report.reason(), "muted"), label("Autor: " + report.contentAuthorAlias(), "muted"),
                        label(report.content() == null ? "Contenido ya eliminado u oculto." : report.content(), "post-body"));
                var hide = button("Ocultar y resolver", "primary");
                hide.setOnAction(e -> {
                    work(() -> { current.hideReported(community.id(), report.id(), "Contenido ocultado tras revisar el reporte."); return true; }, ok -> openModeration(community), err -> { if (!expired(err)) messageDialog(message(err)); });
                });
                var dismiss = button("Descartar reporte", "secondary");
                dismiss.setOnAction(e -> work(() -> { current.resolveReport(community.id(), report.id(), "DISMISSED", "Revisado por el moderador; no se requiere ocultación."); return true; }, ok -> openModeration(community), err -> { if (!expired(err)) messageDialog(message(err)); }));
                card.getChildren().add(new HBox(8, hide, dismiss)); card.getStyleClass().add("activity"); list.getChildren().add(card);
            }
        }, error -> { if (!scope.current(epoch) || expired(error)) return; status.setText(message(error)); });
    }

    private void reloadCommunity(ApiClient.Community community, long expected) {
        if (scope.current(expected)) openCommunity(community, "Cambios guardados.");
    }
    private boolean confirm(String title, String message) {
        var alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(title); alert.setHeaderText(null); return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    private void messageDialog(String message) { new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK).showAndWait(); }
    private String editText(String title, String prompt, String original) {
        var dialog = new Dialog<String>(); dialog.setTitle(title); dialog.setHeaderText(prompt);
        var save = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        var text = new TextArea(original); text.setWrapText(true); text.setPrefRowCount(8);
        dialog.getDialogPane().setContent(text);
        dialog.setResultConverter(button -> button == save ? text.getText() : null);
        return dialog.showAndWait().orElse(null);
    }

    private String message(Throwable error) {
        if (error instanceof ApiClient.Failure failure) {
            return switch (failure.status()) {
                case 400 -> "Revisa los datos y los límites de longitud.";
                case 403 -> "No se pudo validar la solicitud. Cierra sesión y vuelve a entrar.";
                case 404 -> "No tienes acceso a este espacio o tu permiso ha cambiado. Vuelve a Mis comunidades.";
                case 409 -> "La solicitud entra en conflicto con un envío anterior.";
                default -> "El servidor no pudo completar la solicitud. Intenta nuevamente.";
            };
        }
        return "No se pudo confirmar la respuesta. Comprueba la conexión y que el backend siga encendido.";
    }

    private void logout() {
        var previous = api;
        access("Sesión cerrada.");
        long epoch = scope.generation();
        work(() -> { previous.logout(); return true; }, ignored -> {}, error -> {
            if (scope.current(epoch)) accessStatus.setText("Sesión local cerrada. No se pudo confirmar la salida en el servidor.");
        });
    }

    private void closeWindow() {
        if (closing) return;
        closing = true;
        scope.reset();
        var previous = api;
        api = null;
        identity = null;
        root.setTop(null);
        root.setLeft(null);
        root.setBottom(null);
        root.setCenter(label("Cerrando sesión…", "title"));
        root.setDisable(true);
        if (previous == null) {
            workers.shutdownNow();
            stage.close();
            return;
        }
        workers.submit(() -> {
            try { previous.logout(); } catch (Exception error) { previous.clear(); }
            finally {
                Platform.runLater(() -> {
                    workers.shutdownNow();
                    stage.close();
                });
            }
        });
    }
    private <T> void work(Callable<T> operation,Consumer<T> success,Consumer<Throwable> failure) {
        long expected = scope.generation();
        workers.submit(()->{try{T result=operation.call();Platform.runLater(()->{if(scope.current(expected))success.accept(result);});}
            catch(Exception error){Platform.runLater(()->{if(scope.current(expected))failure.accept(error);});}});
    }
}
