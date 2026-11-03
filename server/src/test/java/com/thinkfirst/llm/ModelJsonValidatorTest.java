package com.thinkfirst.llm;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class ModelJsonValidatorTest {
 private final ObjectMapper mapper=new ObjectMapper();
 @Test void acceptsFencedJsonAndDropsExtraRootFields()throws Exception{
  var result=ModelJsonValidator.parse(mapper,"```json\n{\"name\":\"项目\",\"extra\":\"说明\"}\n```",Map.of("name",""));
  assertEquals(Map.of("name","项目"),result);
 }
 @Test void rejectsMissingNestedFieldsAndWrongTypes(){
  assertThrows(IllegalArgumentException.class,()->ModelJsonValidator.parse(mapper,"{\"profile\":{}}",Map.of("profile",Map.of("user",""))));
  assertThrows(IllegalArgumentException.class,()->ModelJsonValidator.parse(mapper,"{\"name\":[]}",Map.of("name","")));
 }
 @Test void rejectsTechniqueAsFramework(){assertThrows(IllegalArgumentException.class,()->ModelJsonValidator.parse(mapper,"{\"s10_framework\":\"few-shot\"}",Map.of("s10_framework","S.C.A.F.F.")));}
}
