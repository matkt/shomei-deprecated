package net.consensys.shomei.storage;

public class WorldStateInMemoryStorageProxyTest extends WorldStateStorageProxyTestBase {

  @Override
  WorldStateStorage getWorldStateStorage() {
    return new InMemoryWorldStateStorage();
  }
}
