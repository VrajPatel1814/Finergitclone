package finergit.ast;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import finergit.FinerGitConfig;

public class FinerJavaMethod extends FinerJavaModule {

  private static final Logger log = LoggerFactory.getLogger(FinerJavaMethod.class);
  private static final String METHOD_FILE_EXTENSION = ".mjava";

  public FinerJavaMethod(final String name, final FinerJavaModule outerModule,
      final FinerGitConfig config) {
    super(name, outerModule, config);
  }

  @Override
  public Path getDirectory() {
    return this.outerModule.getDirectory();
  }

  @Override
  public String getFileName() {
    String name = this.getBaseName() + this.getExtension();
    final int maxFileNameLength = this.config.getMaxFileNameLength();
    if (maxFileNameLength < name.length()) {
      log.warn("\"{}\" is shrinked to {} characters due to too long name", name, maxFileNameLength);
      name = this.shrink(name);
    }
    return name;
  }

  @Override
  public String getExtension() {
    return METHOD_FILE_EXTENSION;
  }
}
