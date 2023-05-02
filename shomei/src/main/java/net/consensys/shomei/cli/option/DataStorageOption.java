package net.consensys.shomei.cli.option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import picocli.CommandLine;

public class DataStorageOption {
  /**
   * Create RPC option.
   *
   * @return the RPC option
   */
  public static DataStorageOption create() {
    return new DataStorageOption();
  }

  @CommandLine.Spec
  CommandLine.Model.CommandSpec spec;

  static final String DEFAULT_DATA_STORAGE_PATH = "./build/data";
  @CommandLine.Option(
      names = {"--data-path"},
      paramLabel = "<PATH>",
      description = "Path to use for persisted storage",
      arity = "1")
  private String dataStoragePath = DEFAULT_DATA_STORAGE_PATH;

  public Path getDataStoragePath() {
    Path path = Path.of(dataStoragePath);
    try {
      Files.createDirectories(path);
    } catch (IOException e) {
      System.err.println("Failed to get or create directories: " + e.getMessage());
    }
    return path;
  }

}
