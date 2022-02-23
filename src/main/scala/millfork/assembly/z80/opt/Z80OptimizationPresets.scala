package millfork.assembly.z80.opt

import millfork.assembly.AssemblyOptimization
import millfork.assembly.z80.ZLine

/**
  * @author Karol Stasiak
  */
object Z80OptimizationPresets {

  val GoodForZ80: List[AssemblyOptimization[ZLine]] = {
    List.fill(5)(
      List.fill(5)(
        AlwaysGoodI80Optimizations.All ++
          AlwaysGoodZ80Optimizations.All ++
          List(
            EmptyParameterStoreRemoval,
            EmptyMemoryStoreRemoval)
      ).flatten ++
        List(ChangeRegisterPairPreferringDE, WordVariableToRegisterOptimization, ByteVariableToRegisterOptimization, ChangeRegisterPairPreferringBC, CompactStackFrame) ++
        LaterIntel8080Optimizations.All ++ LaterI80Optimizations.All
    ).flatten
  }

  val GoodForR800: List[AssemblyOptimization[ZLine]] = {
    List.fill(5)(
      List.fill(5)(
        AlwaysGoodI80Optimizations.All ++
          AlwaysGoodZ80Optimizations.All ++
          AlwaysGoodR800Optimizations.All ++
          List(
            EmptyParameterStoreRemoval,
            EmptyMemoryStoreRemoval)
      ).flatten ++
        List(ChangeRegisterPairPreferringDE, WordVariableToRegisterOptimization, ByteVariableToRegisterOptimization, ChangeRegisterPairPreferringBC, CompactStackFrame) ++
        LaterIntel8080Optimizations.All ++ LaterI80Optimizations.All
    ).flatten
  }

  val GoodForIntel8080: List[AssemblyOptimization[ZLine]] = {
    List.fill(5)(
      List.fill(5)(
        AlwaysGoodI80Optimizations.All ++
          List(
            EmptyParameterStoreRemoval,
            EmptyMemoryStoreRemoval,
          )
      ).flatten ++
        List(ChangeRegisterPairPreferringDE, WordVariableToRegisterOptimization, ByteVariableToRegisterOptimization, ChangeRegisterPairPreferringBC) ++
        LaterIntel8080Optimizations.All ++ LaterI80Optimizations.All
    ).flatten
  }

  val GoodForSharp: List[AssemblyOptimization[ZLine]] = {
    List.fill(5)(
      List.fill(5)(
        AlwaysGoodI80Optimizations.All ++
          List(
            EmptyParameterStoreRemoval,
            EmptyMemoryStoreRemoval)
      ).flatten ++
        List(ChangeRegisterPairPreferringDE, WordVariableToRegisterOptimization, ByteVariableToRegisterOptimization, ChangeRegisterPairPreferringBC) ++
        LaterSharpOptimizations.All ++ LaterI80Optimizations.All
    ).flatten
  }

}
