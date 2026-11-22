package com.bulbat0n.figuraunchained.endpoint;

import java.io.IOException;

public interface EndpointFetcher {
    byte[] fetch() throws IOException;
}
