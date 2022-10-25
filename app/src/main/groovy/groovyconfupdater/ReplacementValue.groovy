package groovyconfupdater

import static java.lang.System.err
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.expr.ConstantExpression

class ReplacementValue {
    public final Expression valueExpression
    public boolean found = false

    public ReplacementValue(Expression valueExpression) {
        this.valueExpression = valueExpression
    }

    public ReplacementValue(String valueCode) {
        def astBuilder = new AstBuilder()
        def v = astBuilder.buildFromString(valueCode)
        if (v.size() != 1) {
            throw new Exception("Replacement value must contain exactly one statement.")
        }
        v = v[0]
        while (true) {
            err.println "v: ${v}"
            if (v instanceof BlockStatement) {
                def statements = v.getStatements()
                if (statements.size() != 1) {
                    throw new Exception("Replacement value must contain exactly one statement.")
                }
                v = statements[0]
            } else if (v instanceof ReturnStatement) {
                v = v.getExpression()
            } else {
                break
            }
        }

        if (!(v instanceof Expression)) {
            throw new Exception("Replacement value is not an expression: ${v}")
        }
        if (!(v instanceof ConstantExpression)) {
            err.println "WARNING: Replacement value is not a constant expression. Did you forget to quote?"
            err.println "         ${v}"
        }
        valueExpression = v
        err.println "valueExpression: ${valueExpression}"
    }

    public String toString() {
        return "ReplacementValue(${valueExpression.getText()})"
    }
}
