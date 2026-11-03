package com.thinkfirst.llm;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
/** 只修正无歧义的包装和布尔字符串，不猜测或补造缺失的需求字段。 */
public final class ModelJsonValidator {
 private ModelJsonValidator(){}
 public static Map<String,Object> parse(ObjectMapper mapper,String source,Map<String,Object> schema) throws com.fasterxml.jackson.core.JsonProcessingException {
  String text=source.trim();
  if(text.startsWith("```"))text=text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
  var node=mapper.readTree(text);
  if(node==null || !node.isObject())throw new IllegalArgumentException("返回值必须是JSON对象");
  Map<String,Object> values=mapper.convertValue(node,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
  if(!values.keySet().containsAll(schema.keySet()))throw new IllegalArgumentException("缺少顶层字段");
  values.keySet().retainAll(schema.keySet());
  for(var entry:schema.entrySet()) values.put(entry.getKey(),check(entry.getValue(),values.get(entry.getKey()),entry.getKey()));
  if(values.containsKey("s10_framework")&&!Arrays.asList(com.thinkfirst.coach.PromptFrameworks.NAMES).contains(values.get("s10_framework")))throw new IllegalArgumentException("s10_framework不是支持的框架");
  return values;
 }
 private static Object check(Object expected,Object actual,String path){
  if(actual==null)throw new IllegalArgumentException(path+"不能为空");
  if(expected instanceof Map<?,?> fields){
   if(!(actual instanceof Map<?,?>))throw new IllegalArgumentException(path+"必须是对象");
   @SuppressWarnings("unchecked") Map<String,Object> result=new LinkedHashMap<>((Map<String,Object>)actual);
   for(var field:fields.entrySet()){String name=String.valueOf(field.getKey());result.put(name,check(field.getValue(),result.get(name),path+"."+name));}
   return result;
  }
  if(expected instanceof List<?> list){
   if(!(actual instanceof List<?> items))throw new IllegalArgumentException(path+"必须是列表");
   if(list.isEmpty())return actual;
   List<Object> result=new ArrayList<>();for(Object item:items)result.add(check(list.get(0),item,path+"[]"));return result;
  }
  if(expected instanceof Boolean){if("true".equals(actual))return true;if("false".equals(actual))return false;if(!(actual instanceof Boolean))throw new IllegalArgumentException(path+"必须是布尔值");}
  if(expected instanceof String && !(actual instanceof String))throw new IllegalArgumentException(path+"必须是文字");
  if(expected instanceof Number && !(actual instanceof Number) && !"待确认".equals(actual))throw new IllegalArgumentException(path+"必须是数字或待确认");
  return actual;
 }
}
