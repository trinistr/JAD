package vt.smt.world.entryPoints;

import vt.smt.Business.AreaChecker;
import vt.smt.Business.Point;
import vt.smt.db.DBUtil;
import vt.smt.world.PointTransport;
import vt.smt.world.user.register.User;
import vt.smt.world.user.session.Session;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Qualifier;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
* points/add
* -> {"authtoken": String, "points": Array[ {"x":String, "y":String, "r":String, "xoff":String, "yoff":String} ]}
* <- {"success": Boolean, "points": Array[ {"x":String, "y":String, "r":String, "xoff":String, "yoff":String, "hit": Boolean} ]}
*
* points/get
* -> {"authtoken": String}
* <- {"success": Boolean, "points": Array[ {"x":String, "y":String, "r":String, "hit": Boolean} ]}
*
***/
@Stateless
@Path("/points")
public class PointChecker {

    @EJB
    private AreaChecker areaChecker;

    @GET
    @Path("/sayHello")
    public String sayHello(){
        return "Hello";
    }

    @javax.ws.rs.POST
    @Path("/hit")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Point doesItHit(Point p){
        p.setHit(areaChecker.doesPointHit(p));
        return p;
    }

    @OPTIONS
    @Path("/add")
    public Response allow_add(){
        Response.ResponseBuilder rb = Response.ok();
        rb.header("Access-Control-Allow-Method","POST");

        rb.header("Access-Control-Allow-Origin","*");
        rb.header("Access-Control-Allow-Headers","Content-Type");
        return rb.build();
    }

    @OPTIONS
    @Path("/hit")
    public Response allow_hit(){
        return allow_add();
    }
    @OPTIONS
    @Path("/get")
    public Response allow_get(){
        return allow_add();
    }

    @javax.ws.rs.POST
    @Path("/add")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response savePoints(PointTransport points){
        Response.ResponseBuilder rb = Response.ok();
        rb.header("Access-Control-Allow-Origin", new String("*"));

        PointTransport response = points;
        rb.entity(response);
        response.setSuccess(false);
        if(points.getAuthToken() == null){
            response.setError("Не указан токен!");
            return rb.build();
        }

        Integer owner_id = Session.getIdByToken(points.getAuthToken());
        if(owner_id == null){
            response.setError("Такого токена нет в моих таблицах!");
            return rb.build();
        }

        response.setSuccess(true);
        User owner = new User();
        owner.setId(owner_id);

        for (Point point : points.getPoints()) {
            point.setHit(areaChecker.doesPointHit(point));
            point.setOwner(owner);
            DBUtil.save(point);
        }

        for (Point point : response.getPoints()) {
            point.setOwner(null);
            point.setPoint_id(null); // don't transfer it!
        }

        return rb.build();
    }

    @javax.ws.rs.POST
    @Path("/get")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPoints(User owner){
        Response.ResponseBuilder rb = Response.ok();
        rb.header("Access-Control-Allow-Origin", "*");

        PointTransport response = new PointTransport();
        rb.entity(response);
        response.setSuccess(false);
        if(owner.getAuthToken() == null){
            response.setError("Укажите токен");
            return rb.build();
        }

        Integer owner_id = Session.getIdByToken(owner.getAuthToken());
        if(owner_id == null){
            response.setError("Нет сессии с данным токеном!");
            return rb.build();
        }

        owner = DBUtil.findUserById(owner_id);
        response.setPoints(DBUtil.findThisGuysPoints(owner));
        for (Point point : response.getPoints()) {
            point.setOwner(null); // we don't want to transfer this information to front-end
        }
        response.setSuccess(true);

        return rb.build();
    }
}
