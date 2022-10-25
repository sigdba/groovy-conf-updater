package groovyconfupdater

import org.codehaus.groovy.syntax.Token
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.stmt.BlockStatement
import groovy.console.ui.AstNodeToScriptVisitor
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.expr.BinaryExpression

class App {
    static Expression keyToPropEx(parts) {
        def varEx = new VariableExpression(parts[0])
        parts = parts.tail()
        if (parts.size() < 1)
            return varEx
        return parts.inject(varEx) { acc, val -> new PropertyExpression(acc, new ConstantExpression(val)) }
    }

    static void updateGroovyFile(String groovyPath, Map<String,ReplacementValue> updates) {
        def inCodeStr = new File(groovyPath).text
        def astBuilder = new AstBuilder()
        def inAsts = astBuilder.buildFromString(inCodeStr)

        if (inAsts.size() != 1) {
            throw new Exception("input AST count != 1")
        }
        if (!(inAsts[0] instanceof BlockStatement)) {
            throw new Exception("input AST is not a BlockStatement")
        }

        def writer = new StringWriter()
        def outVisitor = new AstNodeToScriptVisitor(writer)
        def asnVisitor = new AssignmentVisitor(updates)


        for (node in inAsts) {
            node.visit(asnVisitor)
        }

        def statementsToAdd = asnVisitor.getUnusedReplacements().collect {
            new ExpressionStatement(
                new BinaryExpression(
                    App.keyToPropEx(it.key.split('\\.')),
                    Token.newSymbol("=", 0, 0),
                    it.value.valueExpression
                )
            )
        }

        new BlockStatement(statementsToAdd + inAsts[0].getStatements(), inAsts[0].getVariableScope()).visit(outVisitor)
        println writer

    }

    static void main(String[] args) {
        def updates = System.in.readLines()
            .collect { it.split('=', 2) }
            .collectEntries { [it[0], new ReplacementValue(it[1])] }
        updateGroovyFile(args[0], updates)
    }
}
