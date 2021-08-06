package millfork.compiler.z80

import millfork.CompilationFlag
import millfork.assembly.z80._
import millfork.compiler.{AbstractExpressionCompiler, CompilationContext}
import millfork.env._
import millfork.node.{ConstantArrayElementExpression, Expression, LhsExpression, ZRegister}

import scala.collection.mutable.ListBuffer

/**
  * @author Karol Stasiak
  */
object Z80Multiply {

  /**
    * Compiles A = A * D
    */
  private def multiplication(ctx: CompilationContext): List[ZLine] = {
    List(ZLine(ZOpcode.CALL, NoRegisters,
      ctx.env.get[ThingInMemory]("__mul_u8u8u8").toAddress))
  }

  /**
    * Compiles HL = A * DE
    */
  private def multiplication16And8(ctx: CompilationContext): List[ZLine] = {
    List(ZLine(ZOpcode.CALL, NoRegisters,
      ctx.env.get[ThingInMemory]("__mul_u16u8u16").toAddress))
  }

  /**
    * Compiles HL = BC * DE
    */
  private def multiplication16And16(ctx: CompilationContext): List[ZLine] = {
    List(ZLine(ZOpcode.CALL, NoRegisters,
      ctx.env.get[ThingInMemory]("__mul_u16u16u16").toAddress))
  }

  /**
    * Calculate A = l * r
    */
  def compile8BitMultiply(ctx: CompilationContext, params: List[Expression]): List[ZLine] = {
    var numericConst = 1L
    var otherConst: Constant = NumericConstant(1, 1)
    val filteredParams = params.filter { expr =>
      ctx.env.eval(expr) match {
        case None =>
          true
        case Some(NumericConstant(n, _)) =>
          numericConst *= n
          false
        case Some(c) =>
          otherConst = CompoundConstant(MathOperator.Times, otherConst, c).loByte.quickSimplify
          false
      }
    }
    val productOfConstants = numericConst match {
      case 0 => Constant.Zero
      case 1 => otherConst
      case _ => CompoundConstant(MathOperator.Times, otherConst, NumericConstant(numericConst & 0xff, 1)).quickSimplify
    }
    (filteredParams, otherConst) match {
      case (Nil, NumericConstant(n, _)) => List(ZLine.ldImm8(ZRegister.A, (numericConst * n).toInt))
      case (Nil, _) => List(ZLine.ldImm8(ZRegister.A, productOfConstants))
      case (List(a), NumericConstant(n, _)) => Z80ExpressionCompiler.compileToA(ctx, a) ++ compile8BitMultiply((numericConst * n).toInt)
      case (List(a), _) =>
        compile8BitMultiply(ctx, a, ConstantArrayElementExpression(productOfConstants))
      case (List(a, b), NumericConstant(n, _)) =>
        compile8BitMultiply(ctx, a, b) ++ compile8BitMultiply((numericConst * n).toInt)
      case _ => ???
    }
  }

  /**
    * Calculate A = l * r
    */
  def compile8BitMultiply(ctx: CompilationContext, l: Expression, r: Expression): List[ZLine] = {
    (ctx.env.eval(l), ctx.env.eval(r)) match {
      case (Some(a), Some(b)) => List(ZLine.ldImm8(ZRegister.A, CompoundConstant(MathOperator.Times, a, b).loByte.quickSimplify))
      case (Some(NumericConstant(count, _)), None) => Z80ExpressionCompiler.compileToA(ctx, r) ++ compile8BitMultiply(count.toInt)
      case (None, Some(NumericConstant(count, _))) => Z80ExpressionCompiler.compileToA(ctx, l) ++ compile8BitMultiply(count.toInt)
      case _ =>
        val lb = Z80ExpressionCompiler.compileToA(ctx, l)
        val rb = Z80ExpressionCompiler.compileToA(ctx, r)
        val load = if (lb.exists(Z80ExpressionCompiler.changesDE)) {
          lb ++ List(ZLine.ld8(ZRegister.D, ZRegister.A)) ++ Z80ExpressionCompiler.stashDEIfChanged(ctx, rb)
        } else {
          rb ++ List(ZLine.ld8(ZRegister.D, ZRegister.A)) ++ lb
        }
        load ++ multiplication(ctx)
    }
  }

  /**
    * Calculate A = l * r
    */
  def compile8BitInPlaceMultiply(ctx: CompilationContext, l: LhsExpression, r: Expression): List[ZLine] = {
    ctx.env.eval(r) match {
      case Some(NumericConstant(count, _)) =>
        val (load, store) = Z80ExpressionCompiler.calculateLoadAndStoreForByte(ctx, l)
        load ++ compile8BitMultiply(count.toInt) ++ store
      case Some(c) =>
        val (load, store) = Z80ExpressionCompiler.calculateLoadAndStoreForByte(ctx, l)
        load ++ List(ZLine.ldImm8(ZRegister.D, c)) ++ multiplication(ctx) ++ store
      case _ =>
        val (load, store) = Z80ExpressionCompiler.calculateLoadAndStoreForByte(ctx, l)
        val rb = Z80ExpressionCompiler.compileToA(ctx, r)
        val loadRegisters = if (load.exists(Z80ExpressionCompiler.changesDE)) {
          load ++ List(ZLine.ld8(ZRegister.D, ZRegister.A)) ++ Z80ExpressionCompiler.stashDEIfChanged(ctx, rb)
        } else {
          rb ++ List(ZLine.ld8(ZRegister.D, ZRegister.A)) ++ load
        }
        loadRegisters ++ multiplication(ctx) ++ store
    }
  }

  /**
    * Calculate HL = p / q and/or (if rhsWord then HL else A) = p %% q
    */
  def compileUnsignedWordDivision(ctx: CompilationContext, p: Either[LocalVariableAddressOperand, Expression], q: Expression, modulo: Boolean, rhsWord: Boolean): List[ZLine] = {
    val pb = p match {
      case Right(pp) => Z80ExpressionCompiler.compileToHL(ctx, pp)
      case Left(LocalVariableAddressViaHL) => List(
        ZLine.ld8(ZRegister.A, ZRegister.MEM_HL),
        ZLine.register(ZOpcode.INC_16, ZRegister.HL),
        ZLine.ld8(ZRegister.H, ZRegister.MEM_HL),
        ZLine.ld8(ZRegister.L, ZRegister.A)
      )
      case Left(LocalVariableAddressViaIX(offset)) => List(ZLine.ldViaIx(ZRegister.L, offset), ZLine.ldViaIx(ZRegister.H, offset+1))
      case Left(LocalVariableAddressViaIY(offset)) => List(ZLine.ldViaIy(ZRegister.L, offset), ZLine.ldViaIy(ZRegister.H, offset+1))
    }
    ctx.env.eval(q) match {
      case Some(NumericConstant(0, _)) =>
        ctx.log.error("Unsigned division by zero", q.position)
        return pb
      case Some(NumericConstant(1, _)) =>
        if (modulo) {
          if (rhsWord) return pb :+ ZLine.ldImm16(ZRegister.HL, 0)
          else return pb :+ ZLine.ldImm8(ZRegister.A, 0)
        } else {
          return pb
        }
      case Some(NumericConstant(qc, _)) if qc <= 255 && isPowerOfTwoUpTo15(qc) =>
        val count = Integer.numberOfTrailingZeros(qc.toInt)
        if (modulo) {
          if (rhsWord) return pb ++ List(ZLine.ld8(ZRegister.A, ZRegister.L), ZLine.imm8(ZOpcode.AND, qc.toInt - 1), ZLine.ld8(ZRegister.L, ZRegister.A), ZLine.ldImm8(ZRegister.H, 0))
          else return pb ++ List(ZLine.ld8(ZRegister.A, ZRegister.L), ZLine.imm8(ZOpcode.AND, qc.toInt - 1))
        } else {
          val extendedOps = ctx.options.flag(CompilationFlag.EmitExtended80Opcodes)
          val shiftHL = if (extendedOps) {
            (0L until count).flatMap(_ => List(
              ZLine.register(ZOpcode.SRL, ZRegister.H),
              ZLine.register(ZOpcode.RR, ZRegister.L)
            ))
          } else {
            (0 until count).flatMap(_ => List(
              ZLine.ld8(ZRegister.A, ZRegister.H),
              ZLine.register(ZOpcode.OR, ZRegister.A),
              ZLine.implied(ZOpcode.RRA),
              ZLine.ld8(ZRegister.H, ZRegister.A),
              ZLine.ld8(ZRegister.A, ZRegister.L),
              ZLine.implied(ZOpcode.RRA),
              ZLine.ld8(ZRegister.L, ZRegister.A)
            ))
          }
          return pb ++ shiftHL
        }
      case Some(NumericConstant(256, _)) if rhsWord =>
        if (modulo) return pb ++ List(ZLine.ldImm8(ZRegister.H, 0))
        else  return pb ++ List(ZLine.ld8(ZRegister.L, ZRegister.H), ZLine.ldImm8(ZRegister.H, 0))
      case Some(NumericConstant(qc, _)) if modulo && rhsWord && qc <= 0xffff && isPowerOfTwoUpTo15(qc) =>
        return pb ++ List(ZLine.ld8(ZRegister.A, ZRegister.L), ZLine.imm8(ZOpcode.AND, (qc.toInt >> 8) - 1), ZLine.ld8(ZRegister.H, ZRegister.A))
      case _ =>
    }
    if (!rhsWord) {
      val qb = Z80ExpressionCompiler.compileToA(ctx, q)
      val load = if (qb.exists(Z80ExpressionCompiler.changesHL)) {
        pb ++ Z80ExpressionCompiler.stashHLIfChanged(ctx, qb) ++ List(ZLine.ld8(ZRegister.D, ZRegister.A))
      } else if (pb.exists(Z80ExpressionCompiler.changesDE)) {
        qb ++ List(ZLine.ld8(ZRegister.D, ZRegister.A)) ++ Z80ExpressionCompiler.stashDEIfChanged(ctx, pb)
      } else {
        pb ++ qb ++ List(ZLine.ld8(ZRegister.D, ZRegister.A))
      }
      load :+ ZLine(ZOpcode.CALL, NoRegisters, ctx.env.get[FunctionInMemory]("__divmod_u16u8u16u8").toAddress)
    } else {
      val qb = Z80ExpressionCompiler.compileToDE(ctx, q)
      val load = if (qb.exists(Z80ExpressionCompiler.changesHL)) {
        pb ++ Z80ExpressionCompiler.stashHLIfChanged(ctx, qb)
      } else if (pb.exists(Z80ExpressionCompiler.changesDE)) {
        qb ++ Z80ExpressionCompiler.stashDEIfChanged(ctx, pb)
      } else {
        pb ++ qb
      }
      if (modulo) {
        load :+ ZLine(ZOpcode.CALL, NoRegisters, ctx.env.get[FunctionInMemory]("__divmod_u16u16u16u16").toAddress)
      } else if (ctx.options.flag(CompilationFlag.EmitIntel8080Opcodes)) {
        load ++ List(ZLine(ZOpcode.CALL, NoRegisters, ctx.env.get[FunctionInMemory]("__divmod_u16u16u16u16").toAddress), ZLine.implied(ZOpcode.EX_DE_HL))
      } else {
        load ++ List(
          ZLine(ZOpcode.CALL, NoRegisters, ctx.env.get[FunctionInMemory]("__divmod_u16u16u16u16").toAddress),
          ZLine.ld8(ZRegister.H, ZRegister.D),
          ZLine.ld8(ZRegister.L, ZRegister.E))
      }
    }
  }

  /**
    * Calculate A = p / q or A = p %% q
    */
  def compileUnsignedByteDivision(ctx: CompilationContext, p: Either[LocalVariableAddressOperand, Expression], q: Expression, modulo: Boolean): List[ZLine] = {
    def loadPToA(): List[ZLine] = {
      p match {
        case Right(pp) => Z80ExpressionCompiler.compileToA(ctx, pp)
        case Left(LocalVariableAddressViaHL) => List(ZLine.ld8(ZRegister.A, ZRegister.MEM_HL).position(q.position))
        case Left(LocalVariableAddressViaIX(offset)) => List(ZLine.ldViaIx(ZRegister.A, offset).position(q.position))
        case Left(LocalVariableAddressViaIY(offset)) => List(ZLine.ldViaIy(ZRegister.A, offset).position(q.position))
      }
    }

    ctx.env.eval(q) match {
      case Some(NumericConstant(qq, _)) =>
        if (qq < 0) {
          ctx.log.error("Unsigned division by negative constant", q.position)
          Nil
        } else if (qq == 0) {
          ctx.log.error("Unsigned division by zero", q.position)
          Nil
        } else if (qq == 1) {
          if (modulo) List(ZLine.ldImm8(ZRegister.A, 0).position(q.position))
          else loadPToA()
        } else if (qq > 255) {
          if (modulo) loadPToA()
          else List(ZLine.ldImm8(ZRegister.A, 0))
        } else if (isPowerOfTwoUpTo15(qq)) {
          val mask = (qq - 1).toInt
          val shift = Integer.bitCount(mask)
          val postShiftMask = (1 << (8 - shift)) - 1
          if (modulo) loadPToA() :+ ZLine.imm8(ZOpcode.AND, mask)
          else if (shift == 4 && ctx.options.flag(CompilationFlag.EmitSharpOpcodes)) loadPToA() ++ List(ZLine.register(ZOpcode.SWAP, ZRegister.A), ZLine.imm8(ZOpcode.AND, 15))
          else if (ctx.options.flag(CompilationFlag.EmitExtended80Opcodes)) loadPToA() ++ List.fill(shift)(ZLine.register(ZOpcode.SRL, ZRegister.A))
          else loadPToA() ++ List.fill(shift)(ZLine.implied(ZOpcode.RRCA)) :+ ZLine.imm8(ZOpcode.AND, postShiftMask)
        } else {
          compileUnsignedByteDivisionImpl(ctx, p, qq.toInt, modulo)
        }
      case _ =>
        val call = compileUnsignedWordDivision(ctx, p, q, modulo = modulo, rhsWord = false)
        if (modulo) {
          call
        } else {
          call :+ ZLine.ld8(ZRegister.A, ZRegister.L)
        }
    }
  }
  /**
      * Calculate A = p / q or A = p %% q
      */
    def compileUnsignedByteDivisionImpl(ctx: CompilationContext, p: Either[LocalVariableAddressOperand, Expression], q: Int, modulo: Boolean): List[ZLine] = {
      import ZRegister._
      import ZOpcode._
      val result = ListBuffer[ZLine]()
      result ++= (p match {
        case Right(pp) => Z80ExpressionCompiler.compileToA(ctx, pp)
        case Left(LocalVariableAddressViaHL) => List(ZLine.ld8(ZRegister.A, ZRegister.MEM_HL))
        case Left(LocalVariableAddressViaIX(offset)) => List(ZLine.ldViaIx(ZRegister.A, offset))
        case Left(LocalVariableAddressViaIY(offset)) => List(ZLine.ldViaIy(ZRegister.A, offset))
      })
      result += ZLine.ldImm8(E, 0)

      for (i <- 7.to(0, -1)) {
        if ((q << i) <= 255) {
          val lbl = ctx.nextLabel("dv")
          result += ZLine.imm8(CP, q << i)
          result += ZLine.jumpR(ctx, lbl, IfFlagSet(ZFlag.C))
          result += ZLine.imm8(SUB, q << i)
          result += ZLine.label(lbl)
          result += ZLine.implied(CCF) // TODO: optimize?
          if (ctx.options.flag(CompilationFlag.EmitExtended80Opcodes)) {
            result += ZLine.register(RL, E)
          } else {
            result += ZLine.ld8(D, A)
            result += ZLine.ld8(A, E)
            result += ZLine.implied(RLA)
            result += ZLine.ld8(E, A)
            result += ZLine.ld8(A, D)
          }
        }
      }
      if (!modulo) {
        result += ZLine.ld8(A, E)
      }
      result.toList
    }

  def compile16x16BitMultiplyToHL(ctx: CompilationContext, l: Expression, r: Expression): List[ZLine] = {
    (ctx.env.eval(l), ctx.env.eval(r)) match {
      case (None, Some(c)) =>
        Z80ExpressionCompiler.compileToDE(ctx, l) ++ List(ZLine.ldImm16(ZRegister.BC, c)) ++ multiplication16And16(ctx)
      case (Some(c), None) =>
        Z80ExpressionCompiler.compileToDE(ctx, r) ++ List(ZLine.ldImm16(ZRegister.BC, c)) ++ multiplication16And16(ctx)
      case (Some(c), Some(d)) =>
        List(ZLine.ldImm16(ZRegister.HL, CompoundConstant(MathOperator.Times, c, d).quickSimplify.subword(0)))
      case _ =>
        val ld = Z80ExpressionCompiler.compileToDE(ctx, l)
        val rb = Z80ExpressionCompiler.compileToBC(ctx, r)
        val loadRegisters = (ld.exists(Z80ExpressionCompiler.changesBC), rb.exists(Z80ExpressionCompiler.changesDE)) match {
          case (true, true) => ld ++ Z80ExpressionCompiler.stashDEIfChanged(ctx, rb)
          case (false, true) => rb ++ ld
          case (true, false) => ld ++ rb
          case (false, false) => ld ++ rb
        }
        loadRegisters ++ multiplication16And16(ctx)
    }
  }

  /**
    * Calculate HL = l * r
    */
  def compile16BitMultiplyToHL(ctx: CompilationContext, l: Expression, r: Expression): List[ZLine] = {
    val lType = AbstractExpressionCompiler.getExpressionType(ctx, l)
    val rType = AbstractExpressionCompiler.getExpressionType(ctx, r)
    (lType.size, rType.size) match {
      case (2, 2) => return compile16x16BitMultiplyToHL(ctx, l, r)
      case (1, 2) => return compile16BitMultiplyToHL(ctx, r, l)
      case (2, 1) => if (rType.isSigned) return compile16x16BitMultiplyToHL(ctx, l, r)
      case (1, 1) => // ok
      case _ => ctx.log.fatal("Invalid code path", l.position)
    }
    (ctx.env.eval(l), ctx.env.eval(r)) match {
      case (Some(p), Some(q)) =>
        List(ZLine.ldImm16(ZRegister.HL, CompoundConstant(MathOperator.Times, p, q).quickSimplify))
      case (Some(NumericConstant(c, _)), _) if isPowerOfTwoUpTo15(c) =>
        Z80ExpressionCompiler.compileToHL(ctx, l) ++ List.fill(Integer.numberOfTrailingZeros(c.toInt))(ZLine.registers(ZOpcode.ADD_16, ZRegister.HL, ZRegister.HL))
      case (_, Some(NumericConstant(c, _))) if isPowerOfTwoUpTo15(c) =>
        Z80ExpressionCompiler.compileToHL(ctx, l) ++ List.fill(Integer.numberOfTrailingZeros(c.toInt))(ZLine.registers(ZOpcode.ADD_16, ZRegister.HL, ZRegister.HL))
      case (_, Some(c)) =>
        Z80ExpressionCompiler.compileToDE(ctx, l) ++ List(ZLine.ldImm8(ZRegister.A, c)) ++ multiplication16And8(ctx)
      case _ =>
        val lw = Z80ExpressionCompiler.compileToDE(ctx, l)
        val rb = Z80ExpressionCompiler.compileToA(ctx, r)
        val loadRegisters = lw ++ Z80ExpressionCompiler.stashDEIfChanged(ctx, rb)
        loadRegisters ++ multiplication16And8(ctx)
    }
  }

  /**
    * Calculate l = l * r
    */
  def compile16BitInPlaceMultiply(ctx: CompilationContext, l: LhsExpression, r: Expression): List[ZLine] = {
    compile16BitMultiplyToHL(ctx, l, r) ++ Z80ExpressionCompiler.storeHL(ctx, l, signedSource = false)
  }

  /**
    * Calculate A = count * x
    */
  def compile8BitMultiply(count: Int): List[ZLine] = {
    import millfork.assembly.z80.ZOpcode._
    import ZRegister._
    count match {
      case 0 => List(ZLine.ldImm8(A, 0))
      case 1 => Nil
      case 128 => List(ZLine.implied(RRCA), ZLine.imm8(AND, 0x80))
      case 64 => List(ZLine.implied(RRCA), ZLine.implied(RRCA), ZLine.imm8(AND, 0xC0))
      case 32 => List(ZLine.implied(RRCA), ZLine.implied(RRCA), ZLine.implied(RRCA), ZLine.imm8(AND, 0xE0))
      case n if n > 0 && n.-(1).&(n).==(0) => List.fill(Integer.numberOfTrailingZeros(n))(ZLine.register(ADD, A))
      case _ =>
        ZLine.ld8(E,A) :: Integer.toString(count & 0xff, 2).tail.flatMap{
          case '0' => List(ZLine.register(ADD, A))
          case '1' => List(ZLine.register(ADD, A), ZLine.register(ADD, E))
        }.toList
    }
  }

  private def isPowerOfTwoUpTo15(n: Long): Boolean = if (n <= 0 || n >= 0x8000) false else 0 == ((n-1) & n)
}
