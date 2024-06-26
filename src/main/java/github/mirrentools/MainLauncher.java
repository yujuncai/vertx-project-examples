package github.mirrentools;

import github.mirrentools.core.CoreConstants;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4J2LoggerFactory;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Launcher;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.MicrometerMetricsOptions;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * 程序启动器
 *
 * YU
 */
public class MainLauncher extends Launcher {

  public static void main(String[] args) {
    try {
      getConfig();
      System.setProperty("LOG4J_CONFIGURATION_FILE", LOG4J2_CONFIG);
      InternalLoggerFactory.setDefaultFactory(Log4J2LoggerFactory.INSTANCE);
      System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.Log4j2LogDelegateFactory");
      System.setProperty("vertx.disableDnsResolver", "true");
      new MainLauncher().dispatch(args);
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public void beforeStartingVertx(VertxOptions options) {
    options.setBlockedThreadCheckInterval(1000 * 60 * 5);
    options.setPreferNativeTransport(true);
    options.setHAEnabled(true);
    try {
      boolean monitoring = getConfig().getBoolean("monitoringEnabled", true);
      if (monitoring) {
        MicrometerMetricsOptions metricsOptions = new MicrometerMetricsOptions().setEnabled(true);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        new JvmMemoryMetrics().bindTo(meterRegistry);
        metricsOptions.setMicrometerRegistry(meterRegistry);
        options.setMetricsOptions(metricsOptions);
      }
      if (monitoring != CoreConstants.MONITORING_ENABLED) {
        CoreConstants.MONITORING_ENABLED = monitoring;
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public void beforeDeployingVerticle(DeploymentOptions deploymentOptions) {
    try {
      deploymentOptions.setConfig(getConfig());
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  /**
   * App的配置信息
   */
  private static JsonObject CONFIG;
  /**
   * 日志的所在路径,只用前需要确保已经执行过getConfig()
   */
  private static String LOG4J2_CONFIG;

  /**
   * 读取配置文件
   */
  private static JsonObject getConfig() throws Exception {
    if (CONFIG != null) {
      return CONFIG;
    }
    try {
      String root = System.getProperty("user.dir");
      Path path = Paths.get(root, "data", "config.json");
      CONFIG = new JsonObject(new String(Files.readAllBytes(path)));
      LOG4J2_CONFIG = System.getProperty("user.dir") + CONFIG.getString("log4j2Config");
      System.out.println("Loaded config.json with path: " + path);
      System.out.println("Loaded log4j2.xml with path: " + LOG4J2_CONFIG);
    } catch (Exception ignored) {
      String root = Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("")).getPath();
      root = URLDecoder.decode(root.replace("/target/classes", ""), StandardCharsets.UTF_8);
      root = new File(root).getPath();
      Path path = Paths.get(root, "data", "config.json");
      CONFIG = new JsonObject(new String(Files.readAllBytes(path)));
      LOG4J2_CONFIG = root + CONFIG.getString("log4j2Config");
      System.out.println("Loaded config.json with path: " + path);
      System.out.println("Loaded log4j2.xml with path: " + LOG4J2_CONFIG);
    }
    return CONFIG;
  }
}
