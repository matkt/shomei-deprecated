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

import java.util.concurrent.PriorityBlockingQueue;

public class EvictingPriorityBlockingQueue<E> extends PriorityBlockingQueue<E> {
  private final int maxCapacity;

  public EvictingPriorityBlockingQueue(int capacity) {
    super(capacity);
    this.maxCapacity = capacity;
  }

  @Override
  public boolean offer(E e) {
    // If the queue is already at capacity, remove the lowest priority element
    if (size() >= maxCapacity) {
      E lowestPriorityElement = peek();
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
}
