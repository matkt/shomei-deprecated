/*
 * Copyright ConsenSys Software Inc., 2023
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package net.consensys.shomei.fullsync;

import net.consensys.shomei.observer.TrieLogObserver;

import java.util.Optional;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TrieLogBlockingQueue extends PriorityBlockingQueue<TrieLogObserver.TrieLogIdentifier> {

  public static final long INITIAL_SYNC_BLOCK_NUMBER_RANGE = 500;

  private final long maxCapacity;
  private final Supplier<Long> currentHeadSupplier;
  private final Consumer<Long> onTrieLogMissing;

  public TrieLogBlockingQueue(
      long capacity,
      final Supplier<Long> currentHeadSupplier,
      final Consumer<Long> onTrieLogMissing) {
    super((int) capacity, TrieLogObserver.TrieLogIdentifier::compareTo);
    this.maxCapacity = capacity;
    this.currentHeadSupplier = currentHeadSupplier;
    this.onTrieLogMissing = onTrieLogMissing;
  }

  @Override
  public boolean offer(final TrieLogObserver.TrieLogIdentifier e) {
    // If the queue is already at capacity, remove the lowest priority element
    if (size() >= maxCapacity) {
      TrieLogObserver.TrieLogIdentifier lowestPriorityElement = peek();
      if (comparator().compare(e, lowestPriorityElement) > 0) {
        // Remove the lowest priority element and make space for the new element
        poll();
      } else {
        // The new element has lower priority, so it is not added to the queue
        return false;
      }
    }
    return super.offer(e);
  }

  private Optional<Long> distance(Long e) {
    return isEmpty() ? Optional.empty() : Optional.of(peek().blockNumber() - e);
  }

  public synchronized boolean waitForNewElement() {
    long distance;
    do {
      try {
        distance = distance(currentHeadSupplier.get()).orElse(INITIAL_SYNC_BLOCK_NUMBER_RANGE);
        if (distance < 1) { // remove deprecated trielog
          poll();
        }
        if (distance > 1) {
          onTrieLogMissing.accept(distance);
          startWaiting();
        }
      } catch (RuntimeException e) {
        return false;
      }
    } while (distance != 1);
    return true;
  }

  /**
   * Waits until the minimum required number of entries is reached. This method ensures that there
   * is sufficient blocks in the blockchain before importing. For example, if the minimum required
   * entries is 3 and the network is currently at block 6, Shomei will start importing block 3 once
   * the network reaches block 6, as the minimum required entries are 3. This is necessary to handle
   * reorganizations and give enough time for Besu to send the final version of block. Note: This
   * method is only needed to the testnet environment.
   *
   * @param minimumEntriesRequired The minimum number of entries required before importing.
   */
  public synchronized boolean waitForMinimumEntries(final long minimumEntriesRequired) {
    long nbEntries;
    do {
      try {
        nbEntries = size();
        if (nbEntries < minimumEntriesRequired) {
          startWaiting();
        }
      } catch (RuntimeException e) {
        return false;
      }
    } while (nbEntries < minimumEntriesRequired);
    return true;
  }

  @SuppressWarnings("WaitNotInLoop")
  public synchronized void startWaiting() {
    try {
      wait(TimeUnit.SECONDS.toMillis(5));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public synchronized void notifyNewElementAvailable() {
    notifyAll();
  }
}
