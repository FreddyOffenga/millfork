package millfork.compiler.mos

import millfork.assembly.mos.AssemblyLine
import millfork.compiler.{BranchSpec, CompilationContext, MacroExpander}
import millfork.env._
import millfork.error.ConsoleLogger
import millfork.node._

/**
  * @author Karol Stasiak
  */
object MosMacroExpander extends MacroExpander[AssemblyLine] {

  override def stmtPreprocess(ctx: CompilationContext, stmts: List[ExecutableStatement]): List[ExecutableStatement] = new MosStatementPreprocessor(ctx, stmts)()

  override def prepareAssemblyParams(ctx: CompilationContext, assParams: List[AssemblyOrMacroParam], params: List[Expression], code: List[ExecutableStatement]): (List[AssemblyLine], List[ExecutableStatement]) = {
    var paramPreparation = List[AssemblyLine]()
    var actualCode = code
    var hadRegisterParam = false
    assParams.zip(params).foreach {
      case (AssemblyOrMacroParam(typ, Placeholder(ph, phType), AssemblyParameterPassingBehaviour.ByReference), actualParam) =>
        actualParam match {
          case VariableExpression(vname) =>
            ctx.env.get[ThingInMemory](vname)
          case l: LhsExpression =>
            // TODO: ??
            MosExpressionCompiler.compileByteStorage(ctx, MosRegister.A, l)
          case _ =>
            ctx.log.error("A non-assignable expression was passed to an inlineable function as a `ref` parameter", actualParam.position)
        }
        actualCode = actualCode.map {
          case a@MosAssemblyStatement(_, _, expr, _) =>
            a.copy(expression = expr.replaceVariable(ph, actualParam))
          case x => x
        }
      case (AssemblyOrMacroParam(typ, Placeholder(ph, phType), AssemblyParameterPassingBehaviour.ByConstant), actualParam) =>
        ctx.env.eval(actualParam).getOrElse(ctx.env.errorConstant("Non-constant expression was passed to an inlineable function as a `const` parameter", Some(actualParam), actualParam.position))
        actualCode = actualCode.map {
          case a@MosAssemblyStatement(_, _, expr, _) =>
            a.copy(expression = expr.replaceVariable(ph, actualParam))
          case x => x
        }
      case (AssemblyOrMacroParam(typ, v@RegisterVariable(register, _), AssemblyParameterPassingBehaviour.Copy), actualParam) =>
        if (hadRegisterParam) {
          ctx.log.error("Only one macro assembly function parameter can be passed via a register", actualParam.position)
        }
        hadRegisterParam = true
        paramPreparation = MosExpressionCompiler.compile(ctx, actualParam, Some(typ, v), BranchSpec.None)
      case (AssemblyOrMacroParam(_, _, AssemblyParameterPassingBehaviour.Copy), actualParam) =>
        ???
      case (_, actualParam) =>
    }
    paramPreparation -> stmtPreprocess(ctx, actualCode)
  }
}
