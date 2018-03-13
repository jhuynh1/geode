package org.apache.geode.cache.lucene.internal;

import org.apache.geode.GemFireException;

public class LuceneIndexCreationInProgressException extends GemFireException {
  public LuceneIndexCreationInProgressException(String message) {
    super(message);
  }
}
