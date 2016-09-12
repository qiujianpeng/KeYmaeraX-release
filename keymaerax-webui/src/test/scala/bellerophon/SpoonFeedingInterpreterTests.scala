package bellerophon

import edu.cmu.cs.ls.keymaerax.bellerophon.parser.BelleParser
import edu.cmu.cs.ls.keymaerax.bellerophon.{BelleProvable, SequentialInterpreter, SpoonFeedingInterpreter}
import edu.cmu.cs.ls.keymaerax.btactics.{DebuggingTactics, Idioms, TacticTestBase}
import edu.cmu.cs.ls.keymaerax.btactics.TactixLibrary._
import edu.cmu.cs.ls.keymaerax.core.{Provable, Sequent}
import edu.cmu.cs.ls.keymaerax.hydra.{DBAbstraction, ProofTree}
import edu.cmu.cs.ls.keymaerax.parser.KeYmaeraXProblemParser
import edu.cmu.cs.ls.keymaerax.parser.StringConverter._
import edu.cmu.cs.ls.keymaerax.tacticsinterface.TraceRecordingListener
import testHelper.KeYmaeraXTestTags.SlowTest

import scala.collection.immutable._

/**
  * Tests the spoon-feeding interpreter.
  * Created by smitsch on 8/24/16.
  */
class SpoonFeedingInterpreterTests extends TacticTestBase {

  /** A listener that stores proof steps in the database `db` for proof `proofId`. */
  def listener(db: DBAbstraction, proofId: Int)(tacticName: String, branch: Int) = {
    val trace = db.getExecutionTrace(proofId)
    val globalProvable = trace.lastProvable
    new TraceRecordingListener(db, proofId, trace.executionId.toInt, trace.lastStepId,
      globalProvable, trace.alternativeOrder, branch, recursive = false, tacticName) :: Nil
  }

  "Atomic tactic" should "be simply forwarded to the inner interpreter" in withDatabase { db =>
    val modelContent = "Variables. R x. End. Problem. x>0 -> x>0 End."
    val proofId = db.createProof(modelContent)

    val interpreter = SpoonFeedingInterpreter(listener(db.db, proofId), SequentialInterpreter)
    interpreter(implyR(1), BelleProvable(Provable.startProof(KeYmaeraXProblemParser(modelContent))))

    val tree: ProofTree = ProofTree.ofTrace(db.db.getExecutionTrace(proofId.toInt), proofFinished = false)
    tree.nodes should have size 2
    tree.root.sequent shouldBe Sequent(IndexedSeq(), IndexedSeq("x>0 -> x>0".asFormula))
    tree.root.children should have size 1
    tree.root.children.head.sequent shouldBe Sequent(IndexedSeq("x>0".asFormula), IndexedSeq("x>0".asFormula))
    tree.root.children.head.rule shouldBe "implyR(1)"
  }

  "Sequential tactic" should "be split into atomics before being fed to inner" in withDatabase { db =>
    val modelContent = "Variables. R x. End. Problem. x>0 -> x>0 End."
    val proofId = db.createProof(modelContent)

    val interpreter = SpoonFeedingInterpreter(listener(db.db, proofId), SequentialInterpreter)

    interpreter(implyR(1) & closeId, BelleProvable(Provable.startProof(KeYmaeraXProblemParser(modelContent))))

    val tree: ProofTree = ProofTree.ofTrace(db.db.getExecutionTrace(proofId.toInt), proofFinished = true)
    tree.nodes should have size 3
    tree.root.sequent shouldBe Sequent(IndexedSeq(), IndexedSeq("x>0 -> x>0".asFormula))
    tree.root.children should have size 1
    tree.root.children.head.sequent shouldBe Sequent(IndexedSeq("x>0".asFormula), IndexedSeq("x>0".asFormula))
    tree.root.children.head.rule shouldBe "implyR(1)"
    tree.root.children.head.children should have size 1
    tree.root.children.head.children.head.sequent shouldBe Sequent(IndexedSeq(), IndexedSeq("true".asFormula))
    tree.root.children.head.children.head.rule shouldBe "closeId"
  }

  "Either tactic" should "be explored and only successful outcome stored in database" in withDatabase { db =>
    val modelContent = "Variables. R x. End. Problem. x>0 -> x>0 End."
    val proofId = db.createProof(modelContent)

    val interpreter = SpoonFeedingInterpreter(listener(db.db, proofId), SequentialInterpreter)
    interpreter(implyR(1) & (andR(1) | closeId), BelleProvable(Provable.startProof(KeYmaeraXProblemParser(modelContent))))

    val tree: ProofTree = ProofTree.ofTrace(db.db.getExecutionTrace(proofId.toInt), proofFinished = true)
    tree.nodes should have size 3
    tree.root.sequent shouldBe Sequent(IndexedSeq(), IndexedSeq("x>0 -> x>0".asFormula))
    tree.root.children should have size 1
    tree.root.children.head.sequent shouldBe Sequent(IndexedSeq("x>0".asFormula), IndexedSeq("x>0".asFormula))
    tree.root.children.head.rule shouldBe "implyR(1)"
    tree.root.children.head.children should have size 1
    tree.root.children.head.children.head.sequent shouldBe Sequent(IndexedSeq(), IndexedSeq("true".asFormula))
    tree.root.children.head.children.head.rule shouldBe "closeId"
  }

  "Branch tactic" should "work top-level" in withDatabase { db =>
    val modelContent = "Variables. R x. End. Problem. x>0 -> x>0&x>=0 End."
    val proofId = db.createProof(modelContent)

    val interpreter = SpoonFeedingInterpreter(listener(db.db, proofId), SequentialInterpreter)
    interpreter(implyR(1) & andR(1) & Idioms.<(closeId & done, skip),
      BelleProvable(Provable.startProof(KeYmaeraXProblemParser(modelContent))))

    val tree: ProofTree = ProofTree.ofTrace(db.db.getExecutionTrace(proofId.toInt), proofFinished = true)
    tree.nodes should have size 7
    tree.root.sequent shouldBe Sequent(IndexedSeq(), IndexedSeq("x>0 -> x>0&x>=0".asFormula))
    tree.root.children should have size 1
    tree.root.children.head.sequent shouldBe Sequent(IndexedSeq("x>0".asFormula), IndexedSeq("x>0&x>=0".asFormula))
    tree.root.children.head.rule shouldBe "implyR(1)"
    tree.root.children.head.children should have size 2
    tree.root.children.head.children(0).sequent shouldBe Sequent(IndexedSeq("x>0".asFormula), IndexedSeq("x>0".asFormula))
    tree.root.children.head.children(0).rule shouldBe "andR(1)"
    tree.root.children.head.children(0).children should have size 1
    tree.root.children.head.children(0).children.head.sequent shouldBe Sequent(IndexedSeq(), IndexedSeq("true".asFormula))
    tree.root.children.head.children(0).children.head.rule shouldBe "closeId"
    tree.root.children.head.children(0).children.head.children shouldBe empty
    tree.root.children.head.children(1).sequent shouldBe Sequent(IndexedSeq("x>0".asFormula), IndexedSeq("x>=0".asFormula))
    tree.root.children.head.children(1).rule shouldBe "andR(1)"
    tree.root.children.head.children(1).children should have size 1
    tree.root.children.head.children(1).children.head.sequent shouldBe Sequent(IndexedSeq("x>0".asFormula), IndexedSeq("x>=0".asFormula))
    tree.root.children.head.children(1).children.head.rule shouldBe "nil"
  }

  //@todo need to compute branch indices in global provable, where branches are shifting and new ones created in-place+at the end of a provable
  it should "work with nested branching when branches stay open" ignore withDatabase { db =>
    val modelContent = "Variables. R x. End. Problem. x>0|x>1 -> x>0&x>=0 End."
    val proofId = db.createProof(modelContent)

    val interpreter = SpoonFeedingInterpreter(listener(db.db, proofId), SequentialInterpreter)
    interpreter(implyR(1) & andR(1) & Idioms.<(orL(-1) & Idioms.<(closeId & done, skip), skip),
      BelleProvable(Provable.startProof(KeYmaeraXProblemParser(modelContent))))

    val tree: ProofTree = ProofTree.ofTrace(db.db.getExecutionTrace(proofId.toInt), proofFinished = true)
    tree.nodes should have size 7
    tree.root.sequent shouldBe Sequent(IndexedSeq(), IndexedSeq("x>0 -> x>0&x>=0".asFormula))
    tree.root.children should have size 1
    tree.root.children.head.sequent shouldBe Sequent(IndexedSeq("x>0".asFormula), IndexedSeq("x>0&x>=0".asFormula))
    tree.root.children.head.rule shouldBe "implyR(1)"
    tree.root.children.head.children should have size 2
    tree.root.children.head.children(0).sequent shouldBe Sequent(IndexedSeq("x>0".asFormula), IndexedSeq("x>0".asFormula))
    tree.root.children.head.children(0).rule shouldBe "andR(1)"
    tree.root.children.head.children(0).children should have size 1
    tree.root.children.head.children(0).children.head.sequent shouldBe Sequent(IndexedSeq(), IndexedSeq("true".asFormula))
    tree.root.children.head.children(0).children.head.rule shouldBe "closeId"
    tree.root.children.head.children(0).children.head.children shouldBe empty
    tree.root.children.head.children(1).sequent shouldBe Sequent(IndexedSeq("x>0".asFormula), IndexedSeq("x>=0".asFormula))
    tree.root.children.head.children(1).rule shouldBe "andR(1)"
    tree.root.children.head.children(1).children should have size 1
    tree.root.children.head.children(1).children.head.sequent shouldBe Sequent(IndexedSeq("x>0".asFormula), IndexedSeq("x>=0".asFormula))
    tree.root.children.head.children(1).children.head.rule shouldBe "nil"
  }

  "Parsed tactic" should "record STTT tutorial example 1 steps" taggedAs SlowTest in withDatabase { db =>
    val modelContent = io.Source.fromInputStream(getClass.getResourceAsStream("/examples/tutorials/sttt/example1.kyx")).mkString
    val proofId = db.createProof(modelContent)
    val interpreter = SpoonFeedingInterpreter(listener(db.db, proofId), SequentialInterpreter)

    val tacticText = """implyR('R) & andL('L) & diffCut({`v>=0`}, 1) & <(diffWeaken(1) & prop, diffInd(1))"""
    val tactic = BelleParser(tacticText)
    interpreter(tactic, BelleProvable(Provable.startProof(KeYmaeraXProblemParser(modelContent))))

    val tree: ProofTree = ProofTree.ofTrace(db.db.getExecutionTrace(proofId.toInt), proofFinished = true)
    tree.nodes should have size 11
    //@todo final steps are empty
    tree.nodes.map(_.rule) shouldBe "" :: "implyR('R)" :: "andL('L)" :: "diffCut({`v>=0`},1)" :: "diffCut({`v>=0`},1)" :: "diffWeaken(1)" :: "diffInd(1)" :: "diffInd(1)" :: "prop" :: "" :: "" :: Nil
  }

}