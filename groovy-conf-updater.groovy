import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.syntax.Token
import groovy.console.ui.AstNodeToScriptVisitor
import org.codehaus.groovy.ast.GroovyCodeVisitor
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ArrayExpression
import org.codehaus.groovy.ast.stmt.AssertStatement
import org.codehaus.groovy.ast.expr.AttributeExpression
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.BitwiseNegationExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.expr.BooleanExpression
import org.codehaus.groovy.ast.stmt.BreakStatement
import org.codehaus.groovy.classgen.BytecodeExpression
import org.codehaus.groovy.ast.stmt.CaseStatement
import org.codehaus.groovy.ast.expr.CastExpression
import org.codehaus.groovy.ast.stmt.CatchStatement
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ClosureListExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.stmt.ContinueStatement
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.stmt.DoWhileStatement
import org.codehaus.groovy.ast.expr.EmptyExpression
import org.codehaus.groovy.ast.stmt.EmptyStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.expr.FieldExpression
import org.codehaus.groovy.ast.stmt.ForStatement
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.stmt.IfStatement
import org.codehaus.groovy.ast.expr.LambdaExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.MethodPointerExpression
import org.codehaus.groovy.ast.expr.MethodReferenceExpression
import org.codehaus.groovy.ast.expr.NotExpression
import org.codehaus.groovy.ast.expr.PostfixExpression
import org.codehaus.groovy.ast.expr.PrefixExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.RangeExpression
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.expr.ElvisOperatorExpression
import org.codehaus.groovy.ast.expr.SpreadExpression
import org.codehaus.groovy.ast.expr.SpreadMapExpression
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression
import org.codehaus.groovy.ast.stmt.SwitchStatement
import org.codehaus.groovy.ast.stmt.SynchronizedStatement
import org.codehaus.groovy.ast.expr.TernaryExpression
import org.codehaus.groovy.ast.stmt.ThrowStatement
import org.codehaus.groovy.ast.stmt.TryCatchStatement
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.UnaryMinusExpression
import org.codehaus.groovy.ast.expr.UnaryPlusExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.WhileStatement
import org.codehaus.groovy.ast.CodeVisitorSupport

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
            println "v: ${v}"
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
            println "WARNING: Replacement value is not a constant expression. Did you forget to quote?"
            println "         ${v}"
        }
        valueExpression = v
        println "valueExpression: ${valueExpression}"
    }
}

class AssignmentVisitor extends CodeVisitorSupport {
    private Map<String,ReplacementValue> replacements;
    private BinaryExpression binEx = null
    private int depth = 0
    private def ctx = []
    private def propChain = []

    public AssignmentVisitor(Map<String,ReplacementValue> replacements) {
        this.replacements = replacements
    }

    private void report(String s, Closure cl) {
        println "${' ' * depth * 2}${depth}:${ctx}:${s}"
        depth++
        cl()
        depth--
        println "\n\n"
    }

    private void checkReplacements() {
        def k = (ctx + propChain).join('.')
        if (k in replacements) {
            println "UPDATING: ${k}"
            def rep = replacements[k]
            binEx.setRightExpression(rep.valueExpression)
            rep.found = true
        } else {
            println "IGNORING: ${k}"
        }
    }

    public Map<String,ReplacementValue> getUnusedReplacements() {
        return replacements.findAll { !it.value.found }
    }

	public void visitExpressionStatement(ExpressionStatement statement) {
        report("visitExpressionStatement: ${statement}") {
            super.visitExpressionStatement(statement)
        }
    }

    public void visitMethodCallExpression(MethodCallExpression call) {
        report("visitMethodCallExpression: ${call}") {
            def obj = call.getObjectExpression()
            if (obj instanceof VariableExpression && obj.getName() == 'this') {
                def meth = call.getMethod()
                if (meth instanceof ConstantExpression) {
                    ctx += meth.getText()
                    super.visitMethodCallExpression(call)
                    ctx.removeLast()
                    return
                }
            }
            super.visitMethodCallExpression(call)
        }
    }

    public void visitBinaryExpression(BinaryExpression expression) {
        report("visitBinaryExpression: ${expression}") {
            if (expression.getOperation().getText() == "=") {
                if (binEx != null) throw new Exception("Don't know how to handle nested equals")
                binEx = expression
                expression.getLeftExpression().visit(this)
                checkReplacements()
                binEx = null
                propChain = []
            }
        }
    }

    public void visitVariableExpression(VariableExpression expression) {
        report("visitVariableExpression: ${expression}") {
            if (binEx != null) {
                propChain += expression.getName()
            }
        }
    }

    public void visitConstantExpression(ConstantExpression expression) {
        report("visitConstantExpression: ${expression}") {
            if (binEx != null) {
                propChain += expression.getText()
            }
        }
    }
}

public Expression keyToPropEx(parts) {
    def varEx = new VariableExpression(parts[0])
    parts = parts.tail()
    if (parts.size() < 1)
        return varEx
    return parts.inject(varEx) { acc, val -> new PropertyExpression(acc, new ConstantExpression(val)) }
}

def inCodeStr = new File('applicationNavigator_configuration.groovy').text
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
def asnVisitor = new AssignmentVisitor(["banner.sso.authenticationProvider":new ReplacementValue("'casio'"),
                                        "some.other.value": new ReplacementValue("'something'")])


for (node in inAsts) {
    node.visit(asnVisitor)
}

def statementsToAdd = asnVisitor.getUnusedReplacements().collect {
    new ExpressionStatement(
        new BinaryExpression(
            keyToPropEx(it.key.split('\\.')),
            Token.newSymbol("=", 0, 0),
            it.value.valueExpression
        )
    )
}

new BlockStatement(statementsToAdd + inAsts[0].getStatements(), inAsts[0].getVariableScope()).visit(outVisitor)
println writer

// println "${inAsts[0]}"
