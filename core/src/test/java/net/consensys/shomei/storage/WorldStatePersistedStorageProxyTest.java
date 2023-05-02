package net.consensys.shomei.storage;

import net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentedStorage;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class WorldStatePersistedStorageProxyTest extends WorldStateStorageProxyTestBase {

  @Rule
  public final TemporaryFolder tempData = new TemporaryFolder();
  protected PersistedWorldStateStorage storage;

  @Before
  public void setup() {
    storage = new PersistedWorldStateStorage(
        new RocksDBSegmentedStorage(
            new RocksDBConfigurationBuilder()
                .databaseDir(tempData.getRoot().toPath())
                .build()));
  }
  @After
  public void tearDown() {
    storage.close();
    tempData.delete();
  }

  @Override
  WorldStateStorage getWorldStateStorage() {
    return storage;
  }
}
