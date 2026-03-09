package com.example.servlet.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateEngineTest {

  @Test
  void render_nullTemplate_returnsEmpty() {
    assertEquals("", TemplateEngine.render(null, Map.of()));
  }

  @Test
  void render_emptyTemplate_returnsEmpty() {
    assertEquals("", TemplateEngine.render("", Map.of()));
  }

  @Test
  void render_noVariables_returnsTemplateUnchanged() {
    assertEquals("<p>hello</p>", TemplateEngine.render("<p>hello</p>", Map.of()));
  }

  @Test
  void render_simpleVariable_substituted() {
    String result = TemplateEngine.render("Hello {{name}}!", Map.of("name", "World"));
    assertEquals("Hello World!", result);
  }

  @Test
  void render_missingVariable_replacedWithEmpty() {
    String result = TemplateEngine.render("Hello {{name}}!", Map.of());
    assertEquals("Hello !", result);
  }

  @Test
  void render_xssCharacters_escaped() {
    String result = TemplateEngine.render("{{v}}", Map.of("v", "<script>alert('xss')</script>"));
    assertEquals("&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;", result);
  }

  @Test
  void render_ampersandEscaped() {
    String result = TemplateEngine.render("{{v}}", Map.of("v", "A & B"));
    assertEquals("A &amp; B", result);
  }

  @Test
  void render_dotNotation_resolvesNestedValue() {
    Map<String, Object> data = Map.of("user", Map.of("name", "Alice"));
    String result = TemplateEngine.render("{{user.name}}", data);
    assertEquals("Alice", result);
  }

  @Test
  void render_dotNotation_missingKey_returnsEmpty() {
    Map<String, Object> data = Map.of("user", Map.of("name", "Alice"));
    String result = TemplateEngine.render("{{user.email}}", data);
    assertEquals("", result);
  }

  @Test
  void render_forLoop_overList_expandsItems() {
    Map<String, Object> data = Map.of("items", List.of("a", "b", "c"));
    String result = TemplateEngine.render("{{#for item in items}}-{{item}}{{/for}}", data);
    assertEquals("-a-b-c", result);
  }

  @Test
  void render_forLoop_emptyList_producesNoOutput() {
    Map<String, Object> data = Map.of("items", List.of());
    String result = TemplateEngine.render("{{#for item in items}}-{{item}}{{/for}}", data);
    assertEquals("", result);
  }

  @Test
  void render_forLoop_overArray_expandsItems() {
    Map<String, Object> data = Map.of("items", new Object[]{"x", "y"});
    String result = TemplateEngine.render("{{#for item in items}}[{{item}}]{{/for}}", data);
    assertEquals("[x][y]", result);
  }
}
