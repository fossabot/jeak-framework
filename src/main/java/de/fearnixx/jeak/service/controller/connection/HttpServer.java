package de.fearnixx.jeak.service.controller.connection;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.fearnixx.jeak.reflect.PathParam;
import de.fearnixx.jeak.reflect.RequestParam;
import de.fearnixx.jeak.service.controller.controller.ControllerContainer;
import de.fearnixx.jeak.service.controller.controller.ControllerMethod;
import de.fearnixx.jeak.service.controller.controller.MethodParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Spark;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.function.Function;

/**
 * A wrapper for the http server.
 */
public abstract class HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(HttpServer.class);
    private static final String API_ENDPOINT = "/api";
    private ObjectMapper objectMapper;

    private RestConfiguration restConfiguration;

    public HttpServer(RestConfiguration restConfiguration) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE);
        this.objectMapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        this.objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.NONE);
        this.restConfiguration = restConfiguration;
    }

    /**
     * Start the http server.
     *
     */
    public void start() {
        restConfiguration.getPort().ifPresent(Spark::port);
    }

    /**
     * Register a provided controller at the Server.
     *
     * @param controllerContainer A {@link ControllerContainer}.
     */
    public abstract void registerController(ControllerContainer controllerContainer);

    /**
     * Build the endpoint.
     *
     * @param controllerContainer The controller as {@link ControllerContainer}.
     * @param controllerMethod The method as {@link ControllerMethod}.
     * @return The endpoint as {@link String} created from the {@link ControllerContainer} and {@link ControllerMethod}.
     */
    protected String buildEndpoint(ControllerContainer controllerContainer, ControllerMethod controllerMethod) {
        char DELIMITER = '/';
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(API_ENDPOINT);
        String controllerEndpoint = controllerContainer.getControllerEndpoint();
        if (controllerEndpoint.charAt(0) != DELIMITER) {
            stringBuilder.append(DELIMITER);
        }
        stringBuilder.append(controllerEndpoint);
        String methodEndpoint = controllerMethod.getPath();
        if (methodEndpoint.charAt(0) != DELIMITER) {
            stringBuilder.append(DELIMITER);
        }
        stringBuilder.append(methodEndpoint);
        return stringBuilder.toString().replaceAll("//", "/");
    }

    /**
     * Convert a request option from json.
     *
     * @param string
     * @param request
     * @param methodParameter
     * @return The generated Object.
     */
    protected Object transformRequestOption(String string, Request request, MethodParameter methodParameter) {
        Object retrievedParameter;
        if ("application/json".equals(request.contentType())) {
            retrievedParameter = fromJson(string, methodParameter.getType());
        } else {
            retrievedParameter = string;
        }
        return retrievedParameter;
    }

    /**
     * Retrieve the name from a {@link RequestParam} annotated value. Only call the method, if you are sure the used
     * {@link MethodParameter} is annotated with an {@link RequestParam}.
     *
     * @param methodParameter
     * @return The name of the annotated variable.
     */
    protected String getRequestParamName(MethodParameter methodParameter) {
        Function<Annotation, Object> function = annotation -> ((RequestParam) annotation).name();
        return (String) methodParameter.callAnnotationFunction(function, RequestParam.class).get();
    }

    protected String getPathParamName(MethodParameter methodParameter) {
        Function<Annotation, Object> function = annotation -> ((PathParam) annotation).name();
        return (String) methodParameter.callAnnotationFunction(function, PathParam.class).get();
    }

    protected boolean isCorsEnabled() {
        return restConfiguration.isCorsEnabled();
    }

    protected Map<String, String> loadCorsHeaders() {
        return restConfiguration.getCorsHeaders();
    }

    protected Map<String, String> loadHeaders() {
        Map<String, String> headers = restConfiguration.getHeaders();
        headers.putAll(restConfiguration.getCorsHeaders());
        return headers;
    }

    /**
     * Generate a json representation of the provided object.
     *
     * @param o
     * @return A {@link String} with the object as json if o =! null,
     * an empty {@link String} otherwise.
     * @throws JsonProcessingException
     */
    protected String toJson(Object o) throws JsonProcessingException {
        if (o == null) {
            return "";
        }
        return objectMapper.writeValueAsString(o);
    }

    private Object fromJson(String json, Class<?> clazz) {
        Object deserializedObject = null;
        try {
            deserializedObject = objectMapper.readValue(json, clazz);
        } catch (IOException e) {
            logger.error("There was an error while trying to deserialize json",e);
        }
        return deserializedObject;
    }

}
