/*
 *    Licensed Materials - Property of IBM
 *    5725-I43 (C) Copyright IBM Corp. 2015. All Rights Reserved.
 *    US Government Users Restricted Rights - Use, duplication or
 *    disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
*/

package com.ibm.mfp.sample;

import com.ibm.mfp.adapter.api.AdaptersAPI;
import com.ibm.mfp.adapter.api.ConfigurationAPI;
import com.ibm.mfp.adapter.api.OAuthSecurity;
import io.swagger.annotations.*;
import org.json.JSONException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.logging.Level;
import java.util.logging.Logger;

@SwaggerDefinition(
		info = @Info(
				description = "Sample showing how Redis (or any other caching service) can be used within an adapter. "+
                              "The REST API allows storing and retriving user related information.",
				version = "V8.0.0beta",
				title = "Sample Redis usage in an API",
				termsOfService = "IBM Terms and Conditions apply",
				contact = @Contact(
						name = "Gal Shachor" /*,
                        email = "Gal@Shachor",
                        url = "http://www.ibm.com" */
				),
				license = @License(
						name = "IBM Samples License"
				)
		)
)
@Path("/redis-users")
@Api(value = "Store user information in Redis",
        description = "A sample adapter that uses Redis to store user data"
)
/**
 * A sample adapter resource that shows how a developer can use a Redis cache.
 *
 * In many cases speeding up the response to the application requires the adapter to store some data inside a shared
 * memory case.
 *
 * This sample shows how Redis, a well known in-memory cache/database can be used inside an adapter. The sample is
 * rather synthetic, but comes to show a few concepts:
 * 1. How shared resources can be initialize inside the Adapter application upon deployment of re-initialization
 * 2. How configuration can be used
 * 3. How a resource can access the adapter application to fetch pre-initialized objects
 * 4. Usage of Redis
 *
 * The sample uses the Jedis package to call into the Redis server. While we are not specifically recommending Jedis
 * or Redis, the usage pattern holds.
 */
public class RedisAdapterResource {
	/*
	 * For more info on JAX-RS see https://jax-rs-spec.java.net/nonav/2.0-rev-a/apidocs/index.html
	 */

    @ApiModel(value = "A User",
            description = "The user data saved in the server"
    )
    public static class UserData {

        @ApiModelProperty(required = true, notes = "The user's full name")
        public String name = null;
        @ApiModelProperty(required = true, notes = "The user's age in years")
        public int age = -1;

        @ApiModelProperty(required = true, notes = "The user's ID")
        public String id = null;

        // used by REST marshalling/unmarshalling
        public UserData() {
        }
    }

    @Context
    AdaptersAPI adaptersAPI;

    //Define logger (Standard java.util.Logger)
    static Logger logger = Logger.getLogger(RedisAdapterResource.class.getName());

    //Inject the MFP configuration API:
    @Context
    ConfigurationAPI configApi;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @OAuthSecurity(enabled = false)
    @ApiOperation(value = "Save user information by its ID",
            notes = "Saves the user's data in Redis indexed by the user's ID",
            httpMethod = "POST",
            response = Void.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK",
                    response = Void.class),
            @ApiResponse(code = 500, message = "Cannot connect to the Redis server/Read data",
                    response = Void.class)
    })
    public void saveUserData(final UserData userData) {

        RedisAdapterApplication redisApp = adaptersAPI.getJaxRsApplication(RedisAdapterApplication.class);
        Jedis redisClient = null;
        try {
            redisClient = redisApp.getConnection();

            org.json.JSONObject json = new org.json.JSONObject();
            json.put("id", userData.id);
            json.put("name", userData.name);
            json.put("age", userData.age);

            System.out.println(json.toString(4));

            redisClient.set(userData.id, json.toString(4));
        } catch (final JSONException t) {
            // Could not package as JSON
            logger.log(Level.SEVERE, "Cannot save data", t);
            throw new InternalServerErrorException("Cannot save data", t);
        } catch (final JedisException t) {
            // Redis connectivity issues
            logger.log(Level.SEVERE, "Cannot connect to the Redis server", t);
            throw new InternalServerErrorException("Cannot connect to the Redis server", t);
        }
        finally {
            if (redisClient != null) {
                redisClient.close();
            }
        }
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @OAuthSecurity(enabled = false)
    @ApiOperation(value = "Fetch user information by its ID",
            notes = "Reads the user's data from Redis and return the data JSON encoded",
            httpMethod = "GET",
            response = UserData.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "Cannot connect to the Redis server/Read data",
                    response = Void.class),
            @ApiResponse(code = 404, message = "Could not find ID",
                    response = Void.class)
    })
    public UserData readUserData(@PathParam("id") @ApiParam("The ID of the user") String id) {

        UserData rc = new UserData();
        RedisAdapterApplication redisApp = adaptersAPI.getJaxRsApplication(RedisAdapterApplication.class);
        Jedis redisClient = null;

        try {
            redisClient = redisApp.getConnection();
            final String jsonEnc = redisClient.get(id);

            if (null != jsonEnc) {
                org.json.JSONObject json = new org.json.JSONObject(jsonEnc);

                rc.id = json.getString("id");
                rc.name = json.getString("name");
                rc.age = json.getInt("age");
            } else {
                throw new NotFoundException(String.format("Could not find [%s]", id));
            }
        } catch (final JSONException t) {
            // Could not parse JSON string
            logger.log(Level.SEVERE, "Cannot read a past saved data", t);
            throw new InternalServerErrorException("Cannot read data", t);
        } catch (final JedisException t) {
            // Redis connectivity issues
            logger.log(Level.SEVERE, "Cannot connect to the Redis server", t);
            throw new InternalServerErrorException("Cannot connect to the Redis server", t);
        }
        finally {
            if (redisClient != null) {
                redisClient.close();
            }
        }

        return rc;
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @OAuthSecurity(enabled = false)
    @ApiOperation(value = "Remove the user information",
            notes = "Deletes the user's data from Redis. Return no content",
            httpMethod = "DELETE",
            response = Void.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "Cannot connect to the Redis server",
                    response = Void.class)
    })
    public void deleteUserData(@PathParam("id") @ApiParam("The ID of the user") String id) {
        RedisAdapterApplication redisApp = adaptersAPI.getJaxRsApplication(RedisAdapterApplication.class);
        Jedis redisClient = null;

        try {
            redisClient = redisApp.getConnection();
            redisClient.del(id);
        } catch (final JedisException t) {
            // Redis connectivity issues
            logger.log(Level.SEVERE, "Cannot connect to the Redis server", t);
            throw new InternalServerErrorException("Cannot connect to the Redis server", t);
        }
        finally {
            if (redisClient != null) {
                redisClient.close();
            }
        }
    }
}