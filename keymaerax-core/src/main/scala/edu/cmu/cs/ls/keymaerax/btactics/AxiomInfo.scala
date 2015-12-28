package edu.cmu.cs.ls.keymaerax.btactics

/**
  * Since axioms are always referred to by their names (which are strings), we have the following problems:
  * 1) It's hard to keep everything up to date when a new axiom is added
  * 2) We don't get any static exhaustiveness checking when we case on an axiom
  *
  * AxiomInfo exists to help fix that. An AxiomInfo is just a collection of per-axiom information. The tests for
  * this object dynamically ensure it is exhaustive with respect to AxiomBase and DerivedAxioms. By adding a new
  * field to AxiomInfo you can ensure that all new axioms will have to have that field.
  * Created by bbohrer on 12/28/15.
  */
object AxiomInfo {
  case class AxiomNotFoundException(axiomName: String) extends Exception

  def apply(axiomName: String): AxiomInfo = {
    axiomName match {
      case "chain rule" => new AxiomInfo("chain")
      case "V vacuous" => new AxiomInfo("V")
      case "K modal modus ponens" => new AxiomInfo("K")
      case "I induction" => new AxiomInfo("I")
      case "all instantiate" => new AxiomInfo("Eall")
      case "all eliminate" => new AxiomInfo("Eall")
      case "exists eliminate" => new AxiomInfo("Eexists")
      case "vacuous all quantifier" => new AxiomInfo("Vall")
      case "vacuous exists quantifier" => new AxiomInfo("Vexists")
      case "all dual" => new AxiomInfo("Dall")
      case "exists dual" => new AxiomInfo("Dexists")
      case "const congruence" => new AxiomInfo("CE")
      case "const formula congruence" => new AxiomInfo("CE")
      // [a] modalities and <a> modalities
      case "<> dual" => new AxiomInfo("<>D")
      case "[] dual" => new AxiomInfo("[]D")
      case "[:=] assign" => new AxiomInfo("[:=]")
      case "<:=> assign" => new AxiomInfo("<:=>")
      case "[':=] differential assign" => new AxiomInfo("[':=]")
      case "<':=> differential assign" => new AxiomInfo("<':=>")
      case "[:=] assign equational" => new AxiomInfo("[:=]")
      case "<:=> assign equational" => new AxiomInfo("<:=>")
      case "[:=] assign update" => new AxiomInfo("[:=]")
      case "<:=> assign update" => new AxiomInfo("<:=>")
      case "[:*] assign nondet" => new AxiomInfo("[:*]")
      case  "<:*> assign nondet" => new AxiomInfo("<:*>")
      case "[?] test"    => new AxiomInfo("[?]")
      case "<?> test"    => new AxiomInfo("<?>")
      case "[++] choice" => new AxiomInfo("[++]")
      case "<++> choice" => new AxiomInfo("<++>")
      case "[;] compose" => new AxiomInfo("[;]")
      case "<;> compose" => new AxiomInfo("<;>")
      case "[*] iterate" => new AxiomInfo("[*]")
      case "<*> iterate" => new AxiomInfo("<*>")

      case "DW"              => new AxiomInfo("DW")
      case "DW differential weakening" => new AxiomInfo("DW")
      case "DC differential cut" => new AxiomInfo("DC")
      case "DE differential effect system" => new AxiomInfo("DE")
      case "DE differential effect" => new AxiomInfo("DE")
      case "DE differential effect (system)" => new AxiomInfo("DE")
      case "DI differential invariant" => new AxiomInfo("DI")
      case "DG differential ghost" => new AxiomInfo("DG")
      case "DG differential Lipschitz ghost system" => new AxiomInfo("DG")
      case "DG differential pre-ghost" => new AxiomInfo("DG")
      case "DG++ System" => new AxiomInfo("DG++")
      case "DG++" => new AxiomInfo("DG++")
      case ", commute" => new AxiomInfo(",")
      case "DS differential equation solution" => new AxiomInfo("DS")
      case "Dsol& differential equation solution" => new AxiomInfo("DS&")
      case "Dsol differential equation solution" => new AxiomInfo("DS")
      case "DS& differential equation solution" => new AxiomInfo("DS&")
      case "DX differential skip" => new AxiomInfo("DX")
      case "DX diamond differential skip" => new AxiomInfo("DX")
      // Derivatives
      case "&' derive and" => new AxiomInfo("&'")
      case "|' derive or" => new AxiomInfo("|'")
      case "->' derive imply" => new AxiomInfo("->'")
      case "forall' derive forall" => new AxiomInfo("forall'")
      case "exists' derive exists" => new AxiomInfo("exists'")
      case "c()' derive constant fn" => new AxiomInfo("c()'")
      case "=' derive ="   => new AxiomInfo("='")
      case ">=' derive >=" => new AxiomInfo(">='")
      case ">' derive >"   => new AxiomInfo(">'")
      case "<=' derive <=" => new AxiomInfo("<='")
      case "<' derive <"   => new AxiomInfo("<'")
      case "!=' derive !=" => new AxiomInfo("!=")
      case "-' derive neg"   => new AxiomInfo("-'")
      case "+' derive sum"   => new AxiomInfo("+'")
      case "-' derive minus" => new AxiomInfo("-'")
      case "*' derive product" => new AxiomInfo("*'")
      case "/' derive quotient" => new AxiomInfo("/'")
      case "^' derive power" => new AxiomInfo("^'")
      case "x' derive variable" => new AxiomInfo("x'")
      case "x' derive var"   => new AxiomInfo("x'")

      // derived axioms
      case "' linear" => new AxiomInfo("'")
      case "' linear right" => new AxiomInfo("'")
      case "!& deMorgan" => new AxiomInfo("!&")
      case "!| deMorgan" => new AxiomInfo("!|")
      case "!-> deMorgan" => new AxiomInfo("!->")
      case "!<-> deMorgan" => new AxiomInfo("!<->")
      case "!all" => new AxiomInfo("!all")
      case "!exists" => new AxiomInfo("!exists")
      case "![]" => new AxiomInfo("![]")
      case "!<>" => new AxiomInfo("!<>")
      case "[] split" => new AxiomInfo("S[]")
      case "<> split" => new AxiomInfo("S<>")
      case "[] split left" => new AxiomInfo("S[]")
      case "[] split right" => new AxiomInfo("S[]")
      case "<*> approx" => new AxiomInfo("<*>")
      case "<*> stuck" => new AxiomInfo("<*>")
      case "<'> stuck" => new AxiomInfo("<'>")
      case "[] post weaken" => new AxiomInfo("PW[]")
      case "+<= up" => new AxiomInfo("+<=")
      case "-<= up" => new AxiomInfo("-<=")
      case "<=+ down" => new AxiomInfo("<=+")
      case "<=- down" => new AxiomInfo("<=-")
      case "<-> reflexive" => new AxiomInfo("R<->")
      case "-> distributes over &" => new AxiomInfo("->D&")
      case "-> distributes over <->" => new AxiomInfo("->D<->")
      case "-> weaken" => new AxiomInfo("W->")
      case "!! double negation" => new AxiomInfo("!!")
      case ":= assign dual" => new AxiomInfo("D:=")
      case "[:=] vacuous assign" => new AxiomInfo("V[:=]")
      case "<:=> vacuous assign" => new AxiomInfo("V<:=>")
      case "[*] approx" => new AxiomInfo("A[*]")
      case "exists generalize" => new AxiomInfo("existsG")
      case "all substitute" => new AxiomInfo("allS")
      case "V[:*] vacuous assign nondet" => new AxiomInfo("V[:*]")
      case "V<:*> vacuous assign nondet" => new AxiomInfo("V<:*>")
      case "Domain Constraint Conjunction Reordering" => new AxiomInfo("DCCR")
      case "& commute" => new AxiomInfo("C&")
      case "& associative" => new AxiomInfo("A&")
      case "-> expand" => new AxiomInfo("E->")
      case "-> tautology" => new AxiomInfo("->taut")
      case "\\forall->\\exists" => new AxiomInfo("all->exists")
      case "->true" => new AxiomInfo("->T")
      case "true->" => new AxiomInfo("T->")
      case "&true" => new AxiomInfo("&T")
      case "true&" => new AxiomInfo("T&")
      case "0*" => new AxiomInfo("0*")
      case "0+" => new AxiomInfo("0+")
      case "= reflexive" => new AxiomInfo("R=")
      case "* commute" => new AxiomInfo("C*")
      case "= commute" => new AxiomInfo("C=")
      case "<=" => new AxiomInfo("<=")
      case "= negate" => new AxiomInfo("=-")
      case "!= negate" => new AxiomInfo("!=-")
      case "! <" => new AxiomInfo("!<")
      case "! >" => new AxiomInfo("!>")
      case "< negate" => new AxiomInfo("<-")
      case ">= flip" => new AxiomInfo("F>=")
      case "> flip" => new AxiomInfo("F>")
      case "<" => new AxiomInfo("<")
      case ">" => new AxiomInfo(">")
      case "abs" => new AxiomInfo("abs")
      case "min" => new AxiomInfo("min")
      case "max" => new AxiomInfo("max")
      case "*<= up" => new AxiomInfo("*<=")
      case "1Div<= up" => new AxiomInfo("1/<=")
      case "Div<= up" => new AxiomInfo("/<=")
      case "<=* down" => new AxiomInfo("<=*")
      case "<=1Div down" => new AxiomInfo("<=1/")
      case "<=Div down" => new AxiomInfo("<=/")
      case "! !=" => new AxiomInfo("!!=")
      case "! =" => new AxiomInfo("! =")
      case "! <=" => new AxiomInfo("!<=")
      case "! >=" => new AxiomInfo("!>=")
      case _ => throw new AxiomNotFoundException(axiomName)
    }
  }
}
/** The short name for an axiom is a string intended for use in the UI where space is a concern (e.g. when
  * displaying tree-style proofs). Since the goal is to be as short as possible, they are not required to be
  * unique, but should still be as suggestive as possible of what the axiom does.*/
class AxiomInfo (shortName: String)
