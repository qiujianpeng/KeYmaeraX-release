package edu.cmu.cs.ls.keymaerax.bellerophon

import edu.cmu.cs.ls.keymaerax.core.{Formula, Provable}
import edu.cmu.cs.ls.keymaerax.tags.SummaryTest
import edu.cmu.cs.ls.keymaerax.tools.KeYmaera
import org.scalatest.{FlatSpec, Matchers}
import edu.cmu.cs.ls.keymaerax.parser.StringConverter._
import edu.cmu.cs.ls.keymaerax.btactics.Augmentors.FormulaAugmentor
import edu.cmu.cs.ls.keymaerax.btactics.{AxiomIndex, Context, RandomFormula}
import testHelper.CustomAssertions._

/**
  * Test whether unification algorithm can instantiate axioms correctly.
  *
  * @author Andre Platzer
  */
@SummaryTest
class UnifyAxiomInstantiationTest extends FlatSpec with Matchers {
  KeYmaera.init(Map.empty)

  val randomTrials = 10
  val randomComplexity = 8
  val rand = new RandomFormula()

  val unify = UnificationMatch


  private def matchDirect(axiom: String, instance: Formula): Boolean = {
    val ax: Formula = Provable.axiom(axiom)
    val u = unify(ax, instance)
    u(ax) shouldBe instance
    true
  }

  private def matchKey(axiom: String, instance: Formula): Boolean = {
    val ax: Formula = Provable.axiom(axiom)
    val (keyCtx:Context[_],keyPart) = ax.at(AxiomIndex.axiomIndex(axiom)._1)
    val u = unify(keyPart, instance)
    u(keyPart) shouldBe instance
    true
  }

  "Unification instantiation sample" should "instantiate <>" in {
    matchDirect("<> diamond", "![x:=x+1;{x'=55}]!x>=99 <-> <x:=x+1;{x'=55}>x>=99".asFormula)
  }
  it should "instantiate [:=] assign 1" in {
    matchDirect("[:=] assign", "[x:=z;]x^2>=9 <-> z^2>=9".asFormula)
  }
  it should "instantiate [:=] assign 2" in {
    matchDirect("[:=] assign", "[x:=2*x+1;]x^3>=9*x <-> (2*x+1)^3>=9*(2*x+1)".asFormula)
  }
  it should "instantiate [++]" in {
    matchDirect("[++] choice", "[x:=x+1;++{x:=0;{y'=-2}}]x>=y <-> [x:=x+1;]x>=y & [x:=0;{y'=-2}]x>=y".asFormula)
  }
  it should "instantiate [;]" in {
    matchDirect("[;] compose", "[x:=x+1;{x:=0;{y'=-2}}]x>=y <-> [x:=x+1;][x:=0;{y'=-2}]x>=y".asFormula)
  }
  it should "instantiate [*]" in {
    matchDirect("[*] iterate", "[{x:=x+1;{x:=0;{y'=-2}}}*]x>=y <-> x>=y & [x:=x+1;{x:=0;{y'=-2}}][{x:=x+1;{x:=0;{y'=-2}}}*]x>=y".asFormula)
  }

  "Unification" should "instantiate some schematic axioms" in {
  }


  private val schematicAxioms = "<> diamond" :: "[++] choice" :: "[;] compose" :: "[*] iterate" ::
    "DW" :: "DC differential cut" :: "DE differential effect (system)" :: "DI differential invariance" ::
    "DX differential skip" ::
    "-' derive neg" :: "+' derive sum" :: "-' derive minus" :: "*' derive product" :: "/' derive quotient" ::
    "=' derive =" :: ">=' derive >=" :: ">' derive >" :: "<=' derive <=" :: "<' derive <" :: "!=' derive !=" ::
    "&' derive and" :: "|' derive or" :: "forall' derive forall" :: "exists' derive exists" ::
    "K modal modus ponens" :: "I induction" ::
    "all dual" :: "all eliminate" :: "exists eliminate" ::
    Nil

  "Unification" should "instantiate schematic axioms to random schematic instantiations" in {
    for (ax <- schematicAxioms) {
      for (i <- 1 to randomTrials) {
        val randClue = "Instance produced for " + ax + " in\n\t " + i + "th run of " + randomTrials +
          " random trials,\n\t generated with " + randomComplexity + " random complexity\n\t from seed " + rand.seed

        val inst = withSafeClue("Error generating schematic instance\n\n" + randClue) {
          rand.nextSchematicInstance(Provable.axiom(ax), randomComplexity)
        }

        withSafeClue("Random instance " + inst + "\n\n" + randClue) {
          matchDirect(ax, inst)
        }
      }
    }
  }

}
