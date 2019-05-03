package com.microprofile.samples.services.book.resource;

import com.microprofile.samples.services.book.entity.Book;
import com.microprofile.samples.services.book.persistence.BookBean;
import com.microprofile.samples.services.book.service.NumberService;
import com.microprofile.samples.services.book.service.TokenUtil;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.jwt.Claim;
import org.eclipse.microprofile.jwt.ClaimValue;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.opentracing.Traced;
import sun.rmi.runtime.Log;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.noContent;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;

@ApplicationScoped
@Path("books")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Traced
public class BookResource {

    private static final Logger LOGGER = Logger.getLogger(BookResource.class.getName());

    @Inject
    private BookBean bookBean;

    @Inject
    private NumberService numberService;

    @Inject
    private JsonWebToken jwtPrincipal;

    @Context
    private SecurityContext securityContext;

    @Inject
    @Claim("username")
    private ClaimValue<String> username;

    @Inject
    @Claim("email")
    private ClaimValue<String> email;

    @Inject
    @Claim("jti")
    private ClaimValue<String> jti;

    @GET
    @Path("/{id}")
    @Metered(name = "com.microprofile.samples.services.book.resource.BookResource.findById_meter")
    @Timed(name = "com.microprofile.samples.services.book.resource.BookResource.findById_timer")
    @Operation(summary = "Find a Book by Id")
    @APIResponse(responseCode = "200", content = {@Content(schema = @Schema(implementation = Book.class))})
    public Response findById(@PathParam("id") final Long id) {
        LOGGER.info("findById: " + toIdentityString());
        return bookBean.findById(id)
                .map(Response::ok)
                .orElse(status(NOT_FOUND))
                .build();
    }

    @GET
    @Metered(name = "com.microprofile.samples.services.book.resource.BookResource.findAll_meter")
    @Timed(name = "com.microprofile.samples.services.book.resource.BookResource.findAll_timer")
    public Response findAll() {
        LOGGER.info("findAll: " + toIdentityString());
        return ok(bookBean.findAll()).build();
    }

    @POST
    @Metered(name = "com.microprofile.samples.services.book.resource.BookResource.create_meter")
    @Timed(name = "com.microprofile.samples.services.book.resource.BookResource.create_timer")
//    @RolesAllowed("create")
    public Response create(final Book book, @Context UriInfo uriInfo) {
        LOGGER.info("create: " + toIdentityString());

        final String number = numberService.getNumber();
        book.setIsbn(number);

        final Book created = bookBean.create(book);
        final URI createdURI = uriInfo.getBaseUriBuilder()
                .path("books/{id}")
                .resolveTemplate("id", created.getId())
                .build();
        return Response.created(createdURI).build();
    }

    @PUT
    @Metered(name = "com.microprofile.samples.services.book.resource.BookResource.update_meter")
    @Timed(name = "com.microprofile.samples.services.book.resource.BookResource.update_timer")
    public Response update(final Book book) {
//        securityContext.isUserInRole("update");
        LOGGER.info("update: " + toIdentityString());
        return ok(bookBean.update(book)).build();
    }

    @DELETE
    @Path("/{id}")
    @Metered(name = "com.microprofile.samples.services.book.resource.BookResource.delete_meter")
    @Timed(name = "com.microprofile.samples.services.book.resource.BookResource.delete_timer")
    @RolesAllowed("delete")
    public Response delete(@PathParam("id") final Long id) {
        LOGGER.info("delete: " + toIdentityString());
        bookBean.deleteById(id);
        return noContent().build();
    }

    @GET
    @Path("number")
    public Response number() {
        return Response.ok(numberService.getNumber()).build();
    }

    private String toIdentityString() {
        if (jwtPrincipal == null || jwtPrincipal.getName() == null) {
            return "no authenticated user.";
        }

        final StringBuilder builder = new StringBuilder();

        try {
            builder.append(username);
            builder.append(String.format(" (jti=%s)", jti));
            builder.append(String.format(" (email=%s)", email));
            builder.append(String.format(" (keyId=%s)", TokenUtil.headerOfToken(jwtPrincipal.getRawToken()).get("keyId")));
            builder.append(String.format(" (groups=%s)", StringUtils.join(jwtPrincipal.getGroups(), ", ")));

        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, "Can't build identity for token " + jwtPrincipal.getRawToken(), e);
        }
        return builder.toString();
    }

}