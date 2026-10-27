package com.dramastudio.gateway;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProviderProtocolTest {
  @Test void request保留第六字段参考图且不可变(){
    var refs=new ArrayList<>(List.of("https://oss.example/a.png"));
    var request=new ProviderProtocol.Request("hermes-image","p","",Map.of(),"key",refs);
    refs.add("https://oss.example/b.png");
    assertEquals(List.of("https://oss.example/a.png"),request.referenceImageUrls());
    assertThrows(UnsupportedOperationException.class,()->request.referenceImageUrls().add("x"));
  }
}
