# Managing Business Rules

`BusinessRules` creates, reads, changes and deletes rules from Java and from scripts.

## What it does

A rule is a row in `business_rules`. `BusinessRules` wraps that table. It checks a
rule before saving it, so a bad script never reaches a write.

Java code uses `new BusinessRules()`. Scripts use the `BusinessRules` global.
The HTTP route is the generic table API on `business_rules`. See
[Table editing](../data-browser/table-editing.md).

## From a script

```javascript
var id = BusinessRules.create({
  name: 'No empty modules', collection: 'js_modules', runWhen: 'before',
  onInsert: true, onUpdate: true,
  condition: "current.getValue('content') == ''",
  script: 'current.setAbortAction(true);'
});
var rule = BusinessRules.get(id);           // rule.name, rule.runWhen, rule.active
var all = BusinessRules.list('js_modules'); // a Java list, in run order
BusinessRules.update(id, { orderNo: 10 });
BusinessRules.setActive(id, false);
BusinessRules.delete(id);
```

## From Java

```java
BusinessRules rules = new BusinessRules();
String id = rules.create(BusinessRule.builder()
    .name("No empty modules").collection("js_modules").runWhen("before")
    .onInsert(true).script("current.setAbortAction(true);").build());
BusinessRule rule = rules.get(id);
```

`BusinessRule` lives in `com.example.model`. Its defaults are `orderNo` 100 and
`active` true.

## Methods

| Method | Returns |
|---|---|
| `create(rule or fields)` | The new id, or null when a rule aborted the insert |
| `get(id)` | A rule, or null |
| `list(collection)` | Rules in run order. Null or empty lists every table |
| `update(id, changes)` | true, or false when aborted. An unknown id throws |
| `setActive(id, active)` | Same as `update` |
| `delete(id)` | true, or false when no rule has the id |
| `validate(rule or fields)` | Nothing. Throws when the rule is invalid |

Field names are `name`, `collection`, `runWhen`, `onInsert`, `onUpdate`,
`onDelete`, `condition`, `script`, `orderNo` and `active`. An unknown name throws.

## Validation

`create` and `update` check the rule first. These must hold:

- The name is not blank.
- The collection is a valid table name.
- `runWhen` is `before`, `after` or `async`.
- At least one of `onInsert`, `onUpdate`, `onDelete` is true.
- The script is not blank.
- The script and the condition compile.

A failure throws `IllegalArgumentException`. Nothing is saved. The check does not
run the script, so a runtime error still shows up at write time.

## Writes

Every write goes through `TableRecord`. A rule on `business_rules` itself still
runs. An edit takes effect at once, so test a rule on a quiet table first. Set
`active` to false to switch a rule off.

## Related

- [Table records and business rules](./table-records-and-rules.md) — how rules run
- [Async business rules](./async-business-rules.md) — rules that run off the request thread
- [Table editing](../data-browser/table-editing.md) — the HTTP route
- [Script sandbox](./script-sandbox.md) — what scripts may use
