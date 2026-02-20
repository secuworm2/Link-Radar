package com.secuworm.endpointcollector.domain;

import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.ArrayLiteral;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.ConditionalExpression;
import org.mozilla.javascript.ast.FunctionCall;
import org.mozilla.javascript.ast.InfixExpression;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.NewExpression;
import org.mozilla.javascript.ast.ObjectLiteral;
import org.mozilla.javascript.ast.ObjectProperty;
import org.mozilla.javascript.ast.ParenthesizedExpression;
import org.mozilla.javascript.ast.PropertyGet;
import org.mozilla.javascript.ast.RegExpLiteral;
import org.mozilla.javascript.ast.StringLiteral;
import org.mozilla.javascript.ast.TemplateCharacters;
import org.mozilla.javascript.ast.TemplateLiteral;
import org.mozilla.javascript.ast.VariableInitializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class JsAstEndpointExtractor {
    private static final String[] HTTP_METHODS = new String[]{
        "get",
        "post",
        "put",
        "patch",
        "delete",
        "head",
        "options",
        "all",
        "use",
        "route"
    };

    public List<String> extract(String javascriptSource) {
        if (javascriptSource == null || javascriptSource.trim().isEmpty()) {
            return new ArrayList<>();
        }

        AstRoot root = parseAstRoot(javascriptSource);
        if (root == null) {
            return new ArrayList<>();
        }

        Map<String, String> constants = collectStringConstants(root);
        Set<String> discovered = new LinkedHashSet<>();

        root.visit(node -> {
            if (node instanceof FunctionCall) {
                extractFromFunctionCall((FunctionCall) node, constants, discovered);
            } else if (node instanceof NewExpression) {
                extractFromNewExpression((NewExpression) node, constants, discovered);
            }
            return true;
        });

        return new ArrayList<>(discovered);
    }

    public List<SourceRange> extractRegexLiteralRanges(String javascriptSource) {
        AstRoot root = parseAstRoot(javascriptSource);
        if (root == null) {
            return new ArrayList<>();
        }
        List<SourceRange> ranges = new ArrayList<>();
        root.visit(node -> {
            if (node instanceof RegExpLiteral) {
                int start = node.getAbsolutePosition();
                int end = start + node.getLength();
                if (start >= 0 && end > start) {
                    ranges.add(new SourceRange(start, end));
                }
            }
            return true;
        });
        return ranges;
    }

    private AstRoot parseAstRoot(String javascriptSource) {
        if (javascriptSource == null || javascriptSource.trim().isEmpty()) {
            return null;
        }
        try {
            Parser parser = createParser();
            return parser.parse(javascriptSource, "endpoint-collector-inline.js", 1);
        } catch (Exception ex) {
            return null;
        }
    }

    private Parser createParser() {
        CompilerEnvirons environs = new CompilerEnvirons();
        environs.setLanguageVersion(Context.VERSION_ES6);
        environs.setRecoverFromErrors(true);
        environs.setIdeMode(true);
        return new Parser(environs);
    }

    private Map<String, String> collectStringConstants(AstRoot root) {
        Map<String, String> constants = new HashMap<>();
        root.visit(node -> {
            if (!(node instanceof VariableInitializer)) {
                return true;
            }
            VariableInitializer initializer = (VariableInitializer) node;
            if (!(initializer.getTarget() instanceof Name)) {
                return true;
            }
            Name target = (Name) initializer.getTarget();
            AstNode valueNode = initializer.getInitializer();
            if (valueNode == null) {
                return true;
            }

            String resolved = resolveExpression(valueNode, constants);
            if (isEndpointLike(resolved)) {
                constants.put(target.getIdentifier(), resolved);
            }
            return true;
        });
        return constants;
    }

    private void extractFromFunctionCall(
        FunctionCall call,
        Map<String, String> constants,
        Set<String> discovered
    ) {
        String callableName = flattenCallableName(call.getTarget());
        if (callableName.isEmpty()) {
            return;
        }

        if (isNetworkCall(callableName)) {
            List<AstNode> args = call.getArguments();
            if ("axios".equals(callableName)) {
                extractFromAxiosConfig(args, constants, discovered);
            } else {
                addResolvedArg(discovered, constants, args, 0);
            }
        }

        if (isFrameworkRouteCall(callableName)) {
            List<AstNode> args = call.getArguments();
            addResolvedArg(discovered, constants, args, 0);
            if (callableName.endsWith(".route")) {
                extractRouteObject(args, constants, discovered);
            }
        }
    }

    private void extractFromNewExpression(
        NewExpression expression,
        Map<String, String> constants,
        Set<String> discovered
    ) {
        String callableName = flattenCallableName(expression.getTarget());
        if (!"URL".equals(callableName)) {
            return;
        }
        addResolvedArg(discovered, constants, expression.getArguments(), 0);
    }

    private void extractFromAxiosConfig(
        List<AstNode> args,
        Map<String, String> constants,
        Set<String> discovered
    ) {
        if (args == null || args.isEmpty()) {
            return;
        }
        AstNode first = args.get(0);
        if (first instanceof ObjectLiteral) {
            ObjectLiteral objectLiteral = (ObjectLiteral) first;
            for (ObjectProperty property : objectLiteral.getElements()) {
                String key = objectPropertyKey(property);
                if (!"url".equalsIgnoreCase(key)) {
                    continue;
                }
                String resolved = resolveExpression(property.getRight(), constants);
                addCandidate(discovered, resolved);
            }
            return;
        }
        addResolvedArg(discovered, constants, args, 0);
    }

    private void extractRouteObject(
        List<AstNode> args,
        Map<String, String> constants,
        Set<String> discovered
    ) {
        if (args == null || args.isEmpty()) {
            return;
        }
        AstNode first = args.get(0);
        if (!(first instanceof ObjectLiteral)) {
            return;
        }
        ObjectLiteral objectLiteral = (ObjectLiteral) first;
        for (ObjectProperty property : objectLiteral.getElements()) {
            String key = objectPropertyKey(property);
            if (!"url".equalsIgnoreCase(key) && !"path".equalsIgnoreCase(key)) {
                continue;
            }
            String resolved = resolveExpression(property.getRight(), constants);
            addCandidate(discovered, resolved);
        }
    }

    private void addResolvedArg(
        Set<String> discovered,
        Map<String, String> constants,
        List<AstNode> args,
        int index
    ) {
        if (args == null || args.size() <= index) {
            return;
        }
        String resolved = resolveExpression(args.get(index), constants);
        addCandidate(discovered, resolved);
    }

    private void addCandidate(Set<String> discovered, String candidate) {
        if (!isEndpointLike(candidate)) {
            return;
        }
        discovered.add(candidate.trim());
    }

    private boolean isNetworkCall(String callableName) {
        String normalized = callableName.toLowerCase(Locale.ROOT);
        if ("fetch".equals(normalized) || "axios".equals(normalized)) {
            return true;
        }
        if (normalized.startsWith("axios.")) {
            return true;
        }
        return normalized.endsWith(".open");
    }

    private boolean isFrameworkRouteCall(String callableName) {
        String normalized = callableName.toLowerCase(Locale.ROOT);
        for (String method : HTTP_METHODS) {
            if (normalized.equals("app." + method) || normalized.equals("router." + method) || normalized.equals("fastify." + method)) {
                return true;
            }
        }
        return false;
    }

    private String flattenCallableName(AstNode target) {
        if (target == null) {
            return "";
        }
        if (target instanceof Name) {
            return ((Name) target).getIdentifier();
        }
        if (target instanceof PropertyGet) {
            PropertyGet propertyGet = (PropertyGet) target;
            String left = flattenCallableName(propertyGet.getTarget());
            String right = propertyGet.getProperty() == null ? "" : propertyGet.getProperty().getIdentifier();
            if (left.isEmpty()) {
                return right;
            }
            if (right.isEmpty()) {
                return left;
            }
            return left + "." + right;
        }
        return "";
    }

    private String objectPropertyKey(ObjectProperty property) {
        if (property == null || property.getLeft() == null) {
            return "";
        }
        AstNode left = property.getLeft();
        if (left instanceof Name) {
            return ((Name) left).getIdentifier();
        }
        if (left instanceof StringLiteral) {
            return ((StringLiteral) left).getValue();
        }
        return "";
    }

    private String resolveExpression(AstNode node, Map<String, String> constants) {
        if (node == null) {
            return null;
        }

        if (node instanceof ParenthesizedExpression) {
            return resolveExpression(((ParenthesizedExpression) node).getExpression(), constants);
        }
        if (node instanceof StringLiteral) {
            return ((StringLiteral) node).getValue();
        }
        if (node instanceof Name) {
            String identifier = ((Name) node).getIdentifier();
            String constant = constants.get(identifier);
            if (constant != null && !constant.isEmpty()) {
                return constant;
            }
            return "{var}";
        }
        if (node instanceof TemplateLiteral) {
            return resolveTemplateLiteral((TemplateLiteral) node, constants);
        }
        if (node instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression) node;
            if (infix.getOperator() != Token.ADD) {
                return null;
            }
            String left = resolveExpression(infix.getLeft(), constants);
            String right = resolveExpression(infix.getRight(), constants);
            if (left == null && right == null) {
                return null;
            }
            return safe(left) + safe(right);
        }
        if (node instanceof ConditionalExpression) {
            ConditionalExpression conditional = (ConditionalExpression) node;
            String trueExpression = resolveExpression(conditional.getTrueExpression(), constants);
            if (isEndpointLike(trueExpression)) {
                return trueExpression;
            }
            String falseExpression = resolveExpression(conditional.getFalseExpression(), constants);
            if (isEndpointLike(falseExpression)) {
                return falseExpression;
            }
            return null;
        }
        if (node instanceof ArrayLiteral) {
            ArrayLiteral arrayLiteral = (ArrayLiteral) node;
            if (arrayLiteral.getElements().isEmpty()) {
                return null;
            }
            String first = resolveExpression(arrayLiteral.getElements().get(0), constants);
            return first;
        }

        return null;
    }

    private String resolveTemplateLiteral(TemplateLiteral literal, Map<String, String> constants) {
        StringBuilder builder = new StringBuilder();
        for (AstNode element : literal.getElements()) {
            if (element instanceof TemplateCharacters) {
                builder.append(((TemplateCharacters) element).getValue());
                continue;
            }
            String resolved = resolveExpression(element, constants);
            if (resolved == null || resolved.isEmpty()) {
                builder.append("{var}");
            } else if (isEndpointLike(resolved) && resolved.startsWith("/")) {
                builder.append(resolved);
            } else {
                builder.append("{var}");
            }
        }
        return builder.toString();
    }

    private boolean isEndpointLike(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return true;
        }
        if (normalized.startsWith("/")) {
            return true;
        }
        return normalized.startsWith("./") || normalized.startsWith("../");
    }

    private String safe(String value) {
        return value == null ? "{var}" : value;
    }

    public static class SourceRange {
        private final int startInclusive;
        private final int endExclusive;

        public SourceRange(int startInclusive, int endExclusive) {
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
        }

        public boolean contains(int index) {
            return index >= startInclusive && index < endExclusive;
        }
    }
}
