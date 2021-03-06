package vt.smt.world.entryPoints;

import vt.smt.db.DBUtil;
import vt.smt.world.user.register.RegistrationAnswer;
import vt.smt.world.user.register.User;
import vt.smt.world.user.security.Hasher;
import vt.smt.world.user.session.Session;

import javax.ejb.Stateless;
import javax.persistence.NoResultException;
import javax.persistence.RollbackException;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Created by semitro on 14.12.17.
 *
 * user/login
 * -> {"name": String, "password": String}
 * <- {"success": Boolean, "authtoken":String, "error":String } (токен только при успехе)
 *
 * user/logout
 * -> {"authtoken": String} (имей в виду, что следует его делать именно строкой)
 * <- {"success": Boolean}
 *
 **/
@Stateless
@Path("/user")
public class UserHandler{

    private static final int miminal_password_lenght = 2;

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(User user){
        Response.ResponseBuilder rb = Response.ok();
        rb.header("Access-Control-Allow-Origin", "*");

        RegistrationAnswer response = new RegistrationAnswer();
        rb.entity(response);
        response.setSuccess(false); // just default
        User userInDatabase = null;
        try {
            userInDatabase = DBUtil.findUserByName(user.getName());
        }catch (NoResultException no){
            response.setError("Пользователь с именем '" + user.getName() + "' не зарегистрирован");
            return rb.build();
        }

        if(userInDatabase == null) {
            response.setError("Нет у нас пользователя " + user.getName());
            return rb.build();
        }

        if( user.getPassword() == null
                || !userInDatabase.getPassword().equals(Hasher.getHash(user.getPassword()))){
            response.setError("Неправильный пароль");
            return rb.build();
        }

        response.setSuccess(true);
        if(Session.getUsersToken(userInDatabase.getId()) != null) {
            response.setError("Уже и так вошли)"); // but it's success yet!
            response.setAuthToken(Session.getUsersToken(userInDatabase.getId()));
            return rb.build();
        }

        response.setAuthToken(Session.startSession(userInDatabase.getId()));

        return rb.build();
    }

    @POST
    @Path("/logout")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response logout(User user){
        Response.ResponseBuilder rb = Response.ok();
        rb.header("Access-Control-Allow-Method","POST");
        rb.header("Access-Control-Allow-Origin", "*");
        rb.header("Access-Control-Allow-Headers","Content-Type");
        RegistrationAnswer response = new RegistrationAnswer();
        rb.entity(response);
        response.setSuccess(Session.endSession(user.getAuthToken()));
        return rb.build();
    }

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response register(User newUser){
        Response.ResponseBuilder rb = Response.ok();
        rb.header("Access-Control-Allow-Method","POST");
        rb.header("Access-Control-Allow-Origin", "*");
        rb.header("Access-Control-Allow-Headers","Content-Type");
        //
        RegistrationAnswer response = new RegistrationAnswer();
        rb.entity(response);

        response.setError(checkValidationError(newUser));
        // if the string is not null, it's succeed
        response.setSuccess( (response.getError() == null) );

        if(!response.isSuccess()) // Validation error -> there's no reason to continue
            return rb.build();

        // saving hash of the password into the database
        newUser.setPassword(Hasher.getHash(newUser.getPassword()));

        User alreadyis = null;
        try {
            alreadyis = DBUtil.findUserByName(newUser.getName());
            if(alreadyis != null){
                response.setSuccess(false);
                response.setError("Извините, пользователь с таким именем уже существует");
                return rb.build();
            }
        }catch (RuntimeException e){
            // Всё хорошо, никого нет, продолжнаем
        }

        try {
            DBUtil.save(newUser);
        }catch (RollbackException e){ // ??? when does it happen?
            response.setSuccess(false);
            response.setError("Не могу сохранить его в бд");
            return rb.build();
        }
        // absolutely everything is already ok because we're still alive!
        response.setAuthToken(Session.startSession(newUser.getId()));
        response.setError(null);
        return rb.build();
    }

//    @OPTIONS
//    @Path("/*")
    private Response options_allow(){
        Response.ResponseBuilder rb = Response.ok();
        rb.header("Access-Control-Allow-Method","POST");
        rb.header("Access-Control-Allow-Origin","*");
        rb.header("Access-Control-Allow-Headers","Content-Type");
        return rb.build();
    }

    @OPTIONS
    @Path("/login")
    public Response login_allow(){
        return options_allow();
    }


    @OPTIONS
    @Path("/logout")
    public Response logout_allow(){
        return options_allow();
    }

    @OPTIONS
    @Path("/register")
    public Response register_alldow(){
        return options_allow();
    }

    // if everything is ok, returns null
    private String checkValidationError(User user){
        if(user.getPassword().length() < miminal_password_lenght)
            return "The length of the password is to small (At least 2 symbols are required)";
        if(user.getName().isEmpty())
            return "You've forgotten your name.";

        return null;
    }

}