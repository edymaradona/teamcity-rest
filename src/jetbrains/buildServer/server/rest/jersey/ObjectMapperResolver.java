package jetbrains.buildServer.server.rest.jersey;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.server.rest.model.Constants;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import java.text.SimpleDateFormat;

/**
 * @author Vladislav.Rassokhin
 */
@Provider
@Produces(MediaType.APPLICATION_JSON)
public class ObjectMapperResolver implements ContextResolver<ObjectMapper> {

  final static Logger LOG = Logger.getInstance(ObjectMapperResolver.class.getName());

  private final ObjectMapper myMapper;

  public ObjectMapperResolver() {
    myMapper = new ObjectMapper();
    myMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
    myMapper.setAnnotationIntrospector(new JaxbAnnotationIntrospector());
    myMapper.setDateFormat(new SimpleDateFormat(Constants.TIME_FORMAT));
    myMapper.configure(SerializationConfig.Feature.WRITE_EMPTY_JSON_ARRAYS, true);
//    TODO: Add internal property for debug needs
//    myMapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
  }

  public ObjectMapper getContext(Class<?> type) {
    LOG.info("Used for " + type.getCanonicalName());

//    TODO: Investigate is that code required. Can our mapper been used outside from rest plugin?
//    final String name = type.getName();
//    return name.startsWith("jetbrains.buildServer.server.rest") ? myMapper : null;

    return myMapper;
  }
}