package jetbrains.buildServer.server.rest.model.buildType;

import java.util.HashMap;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.BuildFeatureDescriptorFactory;
import jetbrains.buildServer.serverSide.ParametersDescriptor;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.util.StringUtil;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "feature")
public class PropEntityFeature extends PropEntity{
  public PropEntityFeature() {
  }
  public PropEntityFeature(ParametersDescriptor descriptor) {
    super(descriptor);
  }

  public SBuildFeatureDescriptor createFeature(final BuildFeatureDescriptorFactory factory) {
    if (StringUtil.isEmpty(type)){
      throw new BadRequestException("Created build feature cannot have empty 'type'.");
    }
    return factory.createNewBuildFeature(type, properties == null ? new HashMap<String, String>() : properties.getMap());
  }
}
