package edu.cmu.cs.ls.keymaera.tactics

import edu.cmu.cs.ls.keymaera.core._
import edu.cmu.cs.ls.keymaera.tactics.Tactics._
import scala.Unit
import edu.cmu.cs.ls.keymaera.core.ExpressionTraversal.{TraverseToPosition, StopTraversal, ExpressionTraversalFunction}
import scala.language.postfixOps
import scala.Some
import edu.cmu.cs.ls.keymaera.core.PosInExpr

/**
 * In this object we collect wrapper tactics around the basic rules and axioms.
 *
 * Created by Jan-David Quesel on 4/28/14.
 */
object TacticLibrary {

  object TacticHelper {
    def getFormula(s: Sequent, p: Position): Formula = {
      require(p.inExpr == HereP)
      if(p.isAnte) s.ante(p.getIndex) else s.succ(p.getIndex)
    }
  }
  import TacticHelper._

  /**
   * Tactics for real arithmetic
   */
  
  def desequentialization(s : Sequent) = {
    //TODO-nrf Not sure what to do with pref. Matters in non-taut case.
    if(s.ante.isEmpty && s.succ.isEmpty) False
    else {
      val assumption = 
        if(s.ante.isEmpty) True
        else s.ante.reduce( (l,r) => And(l,r) )

      val implicant =
        if(s.succ.isEmpty) Not(assumption)
        else s.succ.reduce( (l,r) => Or(l,r) )

      if(s.ante.isEmpty) implicant
      else Imply(assumption, implicant)      
    }
  }

  def universalClosure(f: Formula): Formula = Forall(Helper.freeVariables(f).toList, f)

//  def deskolemize(f : Formula) = {
//    val FV = SimpleExprRecursion.getFreeVariables(f)
//    Forall(FV, f)
//  }


  def quantifierEliminationT(toolId: String): Tactic = new Tactic("Quantifier Elimination") {
    override def applicable(node: ProofNode): Boolean = ??? // isFirstOrder

    override def apply(tool: Tool, node: ProofNode): Unit = {
      val t: Tactic = new ConstructionTactic("Mathematica QE") {
        override def applicable(node: ProofNode): Boolean = true

        override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
          LookupLemma.addRealArithLemma(tool, universalClosure(desequentialization(node.sequent))) match {
            case Some((file, id, f)) =>
              f match {
                case Equiv(res, True) => {
                  val t = new ApplyRule(LookupLemma(file, id)) {
                    override def applicable(node: ProofNode): Boolean = true
                  }
                  // reinstantiate quantifiers
                  val pos = new Position(true, node.sequent.ante.length)
                  def reInst(f: Formula): Option[Tactic] = f match {
                    case Forall(v, g) => {
                      val resG = reInst(g)
                      if (v.isEmpty) resG
                      else {
                        val vars = v.map(n => n match {
                          case x: Variable => x
                          case _ => throw new IllegalArgumentException("Can only handle quantifiers over variables")
                        })
                        val tac = (for (n <- vars) yield instantiateT(n, n)(pos)).reduce(seqT)
                        resG match {
                          case Some(t) => Some(tac & t)
                          case None => Some(tac)
                        }
                      }
                    }
                    case _ => None
                  }
                  val tr = reInst(res) match {
                    case Some(tac) => t & EquivLeftT(pos) & AndLeftT(pos) & (tac, NotLeftT(new Position(true, node.sequent.ante.length + 1)) & CloseTrueT(new Position(false, node.sequent.succ.length)))
                    case _ => t
                  }
                  Some(tr & ((AxiomCloseT | findPosSucc(indecisive(true, false)) | findPosAnte(indecisive(true, false, true)))*))
                }
                case _ => println("Only apply QE if the result is true, have " + f.prettyString()); None
              }
            case _ => None
          }
        }
      }
      t.scheduler = Tactics.MathematicaScheduler
      t.continuation = continuation
      t.dispatch(this, node)
    }
  }

  /** *******************************************
    * Basic Tactics
    * *******************************************
    */

  def findPosAnte(posT: PositionTactic): Tactic = new ApplyPositionTactic("FindPosAnte (" + posT.name + ")", posT) {
    override def applicable(p: ProofNode): Boolean = findPosition(p.sequent).isDefined

    override def findPosition(s: Sequent): Option[Position] = {
      for (i <- 0 until s.ante.length) {
        val pos = new Position(true, i)
        if(posT.applies(s, pos)) {
          return Some(pos)
        }
      }
      return None
    }
  }

  def findPosSucc(posT: PositionTactic): Tactic = new ApplyPositionTactic("FindPosSucc (" + posT.name + ")", posT) {
    override def applicable(p: ProofNode): Boolean = findPosition(p.sequent).isDefined

    override def findPosition(s: Sequent): Option[Position] = {
      for (i <- 0 until s.succ.length) {
        val pos = new Position(false, i)
        if(posT.applies(s, pos)) {
          return Some(pos)
        }
      }
      return None
    }
  }

  def AndLeftT: PositionTactic = new PositionTactic("AndLeft") {
    def applies(s: Sequent, p: Position) = if (p.isAnte) s.ante(p.index) match {
      case And(_, _) => true
      case _ => false
    } else false

    def apply(pos: Position): Tactic = new Tactics.ApplyRule(AndLeft(pos)) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, pos)
    }
  }

  def AndLeftFindT: Tactic = findPosAnte(AndLeftT)

  def AndRightT: PositionTactic = new PositionTactic("AndRight") {
    def applies(s: Sequent, p: Position) = if (!p.isAnte) s.succ(p.index) match {
      case And(_, _) => true
      case _ => false
    } else false

    def apply(pos: Position): Tactic = new Tactics.ApplyRule(AndRight(pos)) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, pos)
    }
  }

  def AndRightFindT: Tactic = findPosSucc(AndRightT)

  def OrLeftT: PositionTactic = new PositionTactic("OrLeft") {
    def applies(s: Sequent, p: Position) = if (p.isAnte) s.ante(p.index) match {
      case Or(_, _) => true
      case _ => false
    } else false

    def apply(pos: Position): Tactic = new Tactics.ApplyRule(OrLeft(pos)) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, pos)
    }
  }

  def OrLeftFindT: Tactic = findPosAnte(OrLeftT)

  def OrRightT: PositionTactic = new PositionTactic("OrRight") {
    def applies(s: Sequent, p: Position) = if (!p.isAnte) s.succ(p.index) match {
      case Or(_, _) => true
      case _ => false
    } else false

    def apply(pos: Position): Tactic = new Tactics.ApplyRule(OrRight(pos)) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, pos)
    }
  }

  def OrRightFindT: Tactic = findPosSucc(OrRightT)

  def ImplyLeftT: PositionTactic = new PositionTactic("ImplyLeft") {
    def applies(s: Sequent, p: Position) = if (p.isAnte) s.ante(p.index) match {
      case Imply(_, _) => true
      case _ => false
    } else false

    def apply(pos: Position): Tactic = new Tactics.ApplyRule(ImplyLeft(pos)) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, pos)
    }
  }

  def ImplyLeftFindT: Tactic = findPosAnte(ImplyLeftT)

  def ImplyRightT: PositionTactic = new PositionTactic("ImplyRight") {
    def applies(s: Sequent, p: Position) = !p.isAnte && (s.succ(p.index) match {
      case Imply(_, _) => true
      case _ => false
    })

    def apply(pos: Position): Tactic = new Tactics.ApplyRule(ImplyRight(pos)) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, pos)
    }
  }

  def ImplyRightFindT: Tactic = findPosSucc(ImplyRightT)

  def EquivLeftT: PositionTactic = new PositionTactic("EquivLeft") {
    def applies(s: Sequent, p: Position) = if (p.isAnte) s.ante(p.index) match {
      case Equiv(_, _) => true
      case _ => false
    } else false

    def apply(pos: Position): Tactic = new Tactics.ApplyRule(EquivLeft(pos)) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, pos)
    }
  }

  def EquivLeftFindT: Tactic = findPosAnte(EquivLeftT)

  def EquivRightT: PositionTactic = new PositionTactic("EquivRight") {
    def applies(s: Sequent, p: Position) = !p.isAnte && (s.succ(p.index) match {
      case Equiv(_, _) => true
      case _ => false
    })

    def apply(pos: Position): Tactic = new Tactics.ApplyRule(EquivRight(pos)) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, pos)
    }
  }

  def EquivRightFindT: Tactic = findPosSucc(EquivRightT)

  def NotLeftT: PositionTactic = new PositionTactic("NotLeft") {
    def applies(s: Sequent, p: Position) = if (p.isAnte) s.ante(p.index) match {
      case Not(_) => true
      case _ => false
    } else false

    def apply(pos: Position): Tactic = new Tactics.ApplyRule(NotLeft(pos)) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, pos)
    }
  }

  def NotLeftFindT: Tactic = findPosAnte(NotLeftT)

  def NotRightT: PositionTactic = new PositionTactic("NotRight") {
    def applies(s: Sequent, p: Position) = if (!p.isAnte) s.succ(p.index) match {
      case Not(_) => true
      case _ => false
    } else false

    def apply(pos: Position): Tactic = new Tactics.ApplyRule(NotRight(pos)) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, pos)
    }
  }

  def NotRightFindT: Tactic = findPosSucc(NotRightT)

  def hideT: PositionTactic = new PositionTactic("Hide") {
    def applies(s: Sequent, p: Position) = true

    def apply(pos: Position): Tactic = new Tactics.ApplyRule(if (pos.isAnte) HideLeft(pos) else HideRight(pos)) {
      override def applicable(node: ProofNode): Boolean = true
    }
  }

  def cutT(g: (ProofNode => Option[Formula])): Tactic = new ConstructionTactic("Cut") {
    def applicable(pn: ProofNode): Boolean = g(pn) match {
      case Some(_) => true
      case _ => false
    }

    override def constructTactic(tool: Tool, p: ProofNode): Option[Tactic] = g(p) match {
      case Some(t) =>
        Some(new Tactics.ApplyRule(Cut(t)) {
          override def applicable(node: ProofNode): Boolean = node == p
        })
      case _ => None
    }
  }

  def cutT(f: Formula): Tactic = cutT((x: ProofNode) => Some(f))

  def AxiomCloseT(a: Position, b: Position): Tactic = new Tactics.ApplyRule(AxiomClose(a, b)) {
      override def applicable(node: ProofNode): Boolean = a.isAnte && !b.isAnte && getFormula(node.sequent, a) == getFormula(node.sequent, b)
  }

  def AxiomCloseT: Tactic = new ConstructionTactic("AxiomClose") {
    def constructTactic(tool: Tool, p: ProofNode): Option[Tactic] = findPositions(p.sequent) match {
      case Some((a, b)) => Some(AxiomCloseT(a, b))
      case None => None
    }

    def findPositions(s: Sequent): Option[(Position, Position)] = {
      for (f <- s.ante; g <- s.succ)
        if (f == g) return Some((new Position(true, s.ante.indexOf(f)), new Position(false, s.succ.indexOf(g))))
      None
    }

    override def applicable(node: ProofNode): Boolean = findPositions(node.sequent) match {
      case Some(_) => true
      case _ => false
    }
  }

  def CloseTrueT: PositionTactic = new PositionTactic("CloseTrue") {
    override def applies(s: Sequent, p: Position): Boolean = !p.isAnte && (getFormula(s, p) match {
      case True() => true
      case _ => false
    })

    override def apply(p: Position): Tactic = new ApplyRule(CloseTrue(p)) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
    }
  }
  def CloseFalseT: PositionTactic = new PositionTactic("CloseFalse") {
    override def applies(s: Sequent, p: Position): Boolean = p.isAnte && (getFormula(s, p) match {
      case False() => true
      case _ => false
    })

    override def apply(p: Position): Tactic = new ApplyRule(CloseFalse(p)) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
    }
  }
  def axiomT(id: String): Tactic = Axiom.axioms.get(id) match {
    case Some(_) => new Tactics.ApplyRule(Axiom(id)) {
      override def applicable(node: ProofNode): Boolean = true
    }
    case _ => throw new IllegalArgumentException("Unknown axiom " + id)
  }


  def uniformSubstT(subst: Substitution, delta: (Map[Formula, Formula])) = new ConstructionTactic("Uniform Substitution") {
    def applicable(pn: ProofNode) = true

    def constructTactic(tool: Tool, p: ProofNode): Option[Tactic] = {
      val ante = for (f <- p.sequent.ante) yield delta.get(f) match {
        case Some(frm) => frm
        case _ => f
      }
      val succ = for (f <- p.sequent.succ) yield delta.get(f) match {
        case Some(frm) => frm
        case _ => f
      }
      Some(new Tactics.ApplyRule(UniformSubstitution(subst, Sequent(p.sequent.pref, ante, succ))) {
        override def applicable(node: ProofNode): Boolean = node == p
      })
    }

  }

  // assignment tactic (alpha renaming and then assignment rule)
  def assignmentFindAnte = findPosAnte(assignment)
  def assignmentFindSucc = findPosSucc(assignment)
  def assignmentFind = assignmentFindSucc | assignmentFindAnte
  // it would be great if we could access the same position to apply the imply right rule
  // FIXME: this only works for toplevel positions since there the positions are stable
  def assignmentFindImpl = findPosSucc(assignment & ImplyRightT) | findPosAnte(assignment & ImplyLeftT)

  val assignment = new PositionTactic("Assignment") {
    // for now only on top level
    override def applies(s: Sequent, p: Position): Boolean = {
      (p.inExpr == HereP) && ((if (p.isAnte) s.ante else s.succ)(p.index) match {
        case BoxModality(Assign(Variable(_, _, _), _), _) => true
        case DiamondModality(Assign(Variable(_, _, _), _), _) => true
        case _ => false
      })
    }

    override def apply(p: Position): Tactic = Tactics.weakSeqT(uniquify(p), new ApplyRule(new AssignmentRule(p)) {
      override def applicable(n: ProofNode): Boolean = applies(n.sequent, p)
    })
  }

  val uniquify = new PositionTactic("Uniquify") {
    // for now only on top level
    def getAssignment(s: Sequent, p: Position): Option[(String, Option[Int], Term)] = (if (p.isAnte) s.ante else s.succ)(p.index) match {
        case BoxModality(Assign(Variable(name, i, _), e)) => Some(name, i, e)
        case DiamondModality(Assign(Variable(name, i, _), e)) => Some(name, i, e)
        case a => None
      }
    override def applies(s: Sequent, p: Position): Boolean = (p.inExpr == HereP) && getAssignment(s, p).isDefined


    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        getAssignment(node.sequent, p) match {
          case Some((n, idx, _)) => {
            val vars = Helper.variables(node.sequent).filter((ns: NamedSymbol) => ns.name == n)
            require(vars.size > 0, "The variable we want to rename was not found in the sequent all together " + n + " " + node.sequent)
            // we do not have to rename if there are no name clashes
            if(vars.size > 1) {
              val maxIdx: Option[Int] = (vars.map((ns: NamedSymbol) => ns.index)).foldLeft(None: Option[Int])((acc: Option[Int], i: Option[Int]) => acc match {
                case Some(a) => i match {
                  case Some(b) => if (a < b) Some(b) else Some(a)
                  case None => Some(a)
                }
                case None => i
              })
              val tIdx: Option[Int] = maxIdx match {
                case None => Some(0)
                case Some(a) => Some(a+1)
              }
              Some(new ApplyRule(new AlphaConversion(p, n, idx, n, tIdx)) {
                override def applicable(n: ProofNode): Boolean = n == node
              })
            } else {
              None
            }
          }
          case None => None
        }
      }
    }

  }

  // exhaustive equality rewriting
  // check variable disjointness between left and right side
  def isEquality(s: Sequent, p: Position, checkDisjointness: Boolean = false): Boolean = {
    import Helper.variables
    p.isAnte && p.inExpr == HereP && (s.ante(p.getIndex) match {
      case Equals(_, a, b) => if (checkDisjointness) variables(a).intersect(variables(b)).isEmpty else true
      case ProgramEquals(a, b) => /*if (checkDisjointness) variables(a).intersect(variables(b)).isEmpty else*/ true
      case Equiv(a, b) => /*if (checkDisjointness) variables(a).intersect(variables(b)).isEmpty else*/ true
      case _ => false
    })
  }

  def equalityApplicable(left: Boolean, eqPos: Position, p: Position, s: Sequent): Boolean = {
    import Helper.variables
    var applicable = false
    val (blacklist, f) = s.ante(eqPos.getIndex) match {
      case Equals(_, a, b) => val search = if(left) a else b; println("Searching for " + search)
        (variables(a) ++ variables(b),
          new ExpressionTraversalFunction {
            override def preT(p: PosInExpr, e: Term): Either[Option[StopTraversal], Term] = {
              println("found " + e + " with " + e == search)
              if (e == search) applicable = true
              Left(Some(new StopTraversal {}))
            }
          })
      case ProgramEquals(a, b) => val search = if(left) a else b
        (variables(a) ++ variables(b),
          new ExpressionTraversalFunction {
            override def preP(p: PosInExpr, e: Program): Either[Option[StopTraversal], Program] = {
              if (e == search) applicable = true
              Left(Some(new StopTraversal {}))
            }
          })
      case Equiv(a, b) => val search = if(left) a else b
        (variables(a) ++ variables(b),
          new ExpressionTraversalFunction {
            override def preF(p: PosInExpr, e: Formula): Either[Option[StopTraversal], Formula] = {
              if (e == search) applicable = true
              Left(Some(new StopTraversal {}))
            }
          })
      case _ => throw new IllegalArgumentException("Equality Rewriting not applicable")
    }
    val trav = TraverseToPosition(p.inExpr, f, blacklist)
    val form = (if (p.isAnte) s.ante else s.succ)(p.getIndex)
    ExpressionTraversal.traverse(trav, form)
    applicable
  }

  def equalityRewriting(eqPos: Position, p: Position): Tactic = new ApplyRule(new EqualityRewriting(eqPos, p)) {
    override def applicable(node: ProofNode): Boolean = {
      isEquality(node.sequent, eqPos, true) && (equalityApplicable(true, eqPos, p, node.sequent) || equalityApplicable(false, eqPos, p, node.sequent))
    }
  }

  def equalityRewritingRight(eqPos: Position): PositionTactic = new PositionTactic("Equality Rewriting Right") {

    override def applies(s: Sequent, p: Position): Boolean = isEquality(s, eqPos, true) && equalityApplicable(false, eqPos, p, s)

    override def apply(p: Position): Tactic = equalityRewriting(eqPos, p)
  }

  def equalityRewritingLeft(eqPos: Position): PositionTactic = new PositionTactic("Equality Rewriting Left") {

    override def applies(s: Sequent, p: Position): Boolean = isEquality(s, eqPos, true) && equalityApplicable(true, eqPos, p, s)

    override def apply(p: Position): Tactic = equalityRewriting(eqPos, p)
  }

  def findPosInExpr(s: Sequent, blacklist: Set[NamedSymbol], search: Expr, ignore: Position): Option[Position] =
    findPosInExpr(s, blacklist, search == _, Some(ignore))

  def findPosInExpr(s: Sequent, blacklist: Set[NamedSymbol], test: (Expr => Boolean), filterPos: Option[Position]): Option[Position] = {
    var posInExpr: PosInExpr = null
    val f = new ExpressionTraversalFunction {
      val stop = new StopTraversal {}

      override def preF(p: PosInExpr, e: Formula): Either[Option[StopTraversal], Formula] = if (test(e)) {
        posInExpr = p
        Left(Some(stop))
      } else {
        e match {
          case Forall(v, phi) if (blacklist.map(v.contains).foldLeft(false)(_ || _)) => Left(Some(stop))
          case Exists(v, phi) if (blacklist.map(v.contains).foldLeft(false)(_ || _)) => Left(Some(stop))
          case BoxModality(a, c) if (blacklist.map(a.writes.contains).foldLeft(false)(_ || _)) => Left(Some(stop))
          case DiamondModality(a, c) if (blacklist.map(a.writes.contains).foldLeft(false)(_ || _)) => Left(Some(stop))
          case _ => Left(None)
        }
      }

      override def preP(p: PosInExpr, e: Program): Either[Option[StopTraversal], Program] = if (test(e)) {
        posInExpr = p
        Left(Some(stop))
      } else Left(None)

      override def preT(p: PosInExpr, e: Term): Either[Option[StopTraversal], Term] = {
        if (test(e)) {
          posInExpr = p
          Left(Some(stop))
        } else Left(None)
      }


      override def preG(p: PosInExpr, e: Game): Either[Option[StopTraversal], Game] = if (test(e)) {
          posInExpr = p
          Left(Some(stop))
        } else Left(None)

    }
    val ignore = filterPos match {
      case Some(p) => p
      case None => null
    }
    for(i <- 0 until s.ante.length) {
      if(ignore == null || !ignore.isAnte || ignore.getIndex != i) {
        ExpressionTraversal.traverse(f, s.ante(i))
        if (posInExpr != null) {
          return Some(new Position(true, i, posInExpr))
        }
      }
    }
    for(i <- 0 until s.succ.length) {
      if(ignore == null || ignore.isAnte || ignore.getIndex != i) {
        ExpressionTraversal.traverse(f, s.succ(i))
        if (posInExpr != null) {
          return Some(new Position(false, i, posInExpr))
        }
      }
    }
    None
  }

  def findPosInExpr(left: Boolean, s: Sequent, eqPos: Position): Option[Position] = {
    val eq = s.ante(eqPos.getIndex)
    val blacklist = Helper.variables(eq)
    val search: Expr = eq match {
      case Equals(_, a, b) => if(left) a else b
      case ProgramEquals(a, b) => if(left) a else b
      case Equiv(a, b) => if(left) a else b
      case _ => throw new IllegalArgumentException("Equality Rewriting does not work for " + eq)
    }
    findPosInExpr(s, blacklist, search, eqPos)
  }

  def eqRewritePos(left: Boolean, eqPos: Position): Tactic = new ConstructionTactic("Apply Equality Left") {
    require(eqPos.isAnte && eqPos.inExpr == HereP, "Equalities for rewriting have to be in the antecedent")

    override def applicable(node: ProofNode): Boolean = findPosInExpr(left, node.sequent, eqPos).isDefined

    override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
      findPosInExpr(left, node.sequent, eqPos) match {
        case Some(p) =>
          val t = equalityRewriting(eqPos, p)
          val hide = hideT(new Position(p.isAnte, p.getIndex, HereP))
          Some(t & hide)
        case None => None
      }
    }
  }

  def eqLeft(exhaustive: Boolean): PositionTactic = new PositionTactic("Find Equality and Apply Right to Left") {
    override def applies(s: Sequent, p: Position): Boolean = p.isAnte && isEquality(s, p, true) && findPosInExpr(true, s, p).isDefined

    override def apply(p: Position): Tactic = if(exhaustive) eqRewritePos(true, p)* else eqRewritePos(true, p)
  }

  val eqLeftFind = findPosAnte(eqLeft(false))

  val eqLeftFindExhaustive = findPosAnte(eqLeft(true))

  def eqRight(exhaustive: Boolean): PositionTactic = new PositionTactic("Find Equality and Apply Left to Right") {
    override def applies(s: Sequent, p: Position): Boolean = p.isAnte && isEquality(s, p, true) && findPosInExpr(false, s, p).isDefined

    override def apply(p: Position): Tactic = if(exhaustive) eqRewritePos(false, p)* else eqRewritePos(false, p)
  }

  val eqRightFind = findPosAnte(eqRight(false))

  val eqRightFindExhaustive = findPosAnte(eqRight(true))

  // axiom wrappers
  // TODO: Use findPosInExpr to find a position that matches the left side of the axiom and cut in the resulting instance
  // we start with just using findPos to get a top level position

  abstract class AxiomTactic(name: String, axiomName: String) extends PositionTactic(name) {
    val axiom = Axiom.axioms.get(axiomName)
    def applies(f: Formula): Boolean
    final override def applies(s: Sequent, p: Position): Boolean = axiom.isDefined && applies(getFormula(s, p))

    //@TODO Add contract that applies(f) <=> \result.isDefined
    def constructInstanceAndSubst(f: Formula): Option[(Formula, Substitution)]

    //@TODO Add contract that applies()=>\result fine
    override def apply(pos: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, pos)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        axiom match {
          case Some(a) =>
            constructInstanceAndSubst(getFormula(node.sequent, pos)) match {
              case Some((axiomInstance, subst)) =>
                val eqPos = new Position(true, node.sequent.ante.length, HereP)
                //@TODO Prefer simpler sequent proof rule for <->left rather than congruence rewriting if the position to use it on is on top-level of sequent
                val branch1Tactic = equalityRewriting(eqPos, pos) & (hideT(eqPos) & hideT(pos))
                // hide in reverse order since hiding changes positions
                val hideAllAnte = for(i <- node.sequent.ante.length - 1 to 0 by -1) yield hideT(new Position(true, i))
                // this will hide all the formulas in the current succedent (the only remaining one will be the one we cut in)
                val hideAllSuccButLast = for(i <- node.sequent.succ.length - 1 to 0 by -1) yield hideT(new Position(false, i))
                //@TODO Insert contract tactic after hiding all which checks that exactly the intended axiom formula remains and nothing else.
                //@TODO Introduce a reusable tactic that hides all formulas except the ones given as argument and is followed up by a contract ensuring that exactly those formuals remain.
                val branch2Tactic = ((hideAllAnte ++ hideAllSuccButLast).reduce(seqT)) ~ (uniformSubstT(subst, Map(axiomInstance -> a)) & (axiomT(axiomName) & AxiomCloseT))
                Some(cutT(axiomInstance) &(branch1Tactic, branch2Tactic))
              case None => None
            }
          case None => None
        }
      }
    }

  }

  // [?] test
  def boxTestT: PositionTactic = new AxiomTactic("[?] test", "[?] test") {
    override def applies(f: Formula): Boolean = f match {
      case BoxModality(Test(_), _) => true
      case _ => false
    }

    override def constructInstanceAndSubst(f: Formula): Option[(Formula, Substitution)] = f match {
      case BoxModality(Test(h), p) =>
        // construct substitution
        val aH = PredicateConstant("H")
        val aP = PredicateConstant("p")
        val l = List(new SubstitutionPair(aH, h), new SubstitutionPair(aP, p))
        // construct axiom instance: [?H]p <-> (H -> p).
        val g = Imply(h, p)
        val axiomInstance = Equiv(f, g)
        Some(axiomInstance, new Substitution(l))
      case _ => None
    }

  }

  // [;] compose
  def boxSeqT: PositionTactic = new AxiomTactic("[;] compose", "[;] compose") {
    override def applies(f: Formula): Boolean = f match {
      case BoxModality(Sequence(_), _) => true
      case _ => false
    }

    override def constructInstanceAndSubst(f: Formula): Option[(Formula, Substitution)] = f match {
      case BoxModality(Sequence(a, b), p) =>
        // construct substitution
        val aA = ProgramConstant("a")
        val aB = ProgramConstant("b")
        val aP = PredicateConstant("p")
        val l = List(new SubstitutionPair(aA, a), new SubstitutionPair(aB, b), new SubstitutionPair(aP, p))
        // construct axiom instance: [ a; b ]p <-> [a][b]p.
        val g = BoxModality(a, BoxModality(b, p))
        val axiomInstance = Equiv(f, g)
        Some(axiomInstance, new Substitution(l))
      case _ => None
    }

  }

  def modusPonensT(assumption: Position, implication: Position): Tactic = new ConstructionTactic("Modus Ponens") {
    override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
      val p = new Position(true, assumption.getIndex - (if(assumption.getIndex > implication.getIndex) 1 else 0))
      Some(ImplyLeftT(implication) & (AxiomCloseT(p, new Position(false, node.sequent.succ.length)), hideT(assumption)))
    }

    override def applicable(node: ProofNode): Boolean = assumption.isAnte && implication.isAnte &&
      ((getFormula(node.sequent, assumption), getFormula(node.sequent, implication)) match {
      case (a, Imply(b, c)) if (a == b) => true
      case (a, b) => false
    })
  }

  // Quantifier Instantiation
  def instantiateT(quantified: Variable, instance: Term): PositionTactic = new PositionTactic("Quantifier Instantiation") {
    val axiomName = "Quantifier Instantiation"
    val axiom = Axiom.axioms.get(axiomName)
    require(axiom.isDefined)

    override def applies(s: Sequent, p: Position): Boolean = p.isAnte && (getFormula(s, p) match {
      case Forall(_, _) => true
      case _ => false
    })

    override def apply(pos: Position): Tactic = new ConstructionTactic("Quantifier Instantiation") {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, pos)

      def replace(f: Formula)(o: Variable, n: Term): Formula = ExpressionTraversal.traverse(new ExpressionTraversalFunction {
        override def postF(p: PosInExpr, e: Formula): Either[Option[StopTraversal], Formula] = e match {
          case Forall(v, f) => Right(Forall(v.map((name: NamedSymbol) => if(name == o) { require(n.isInstanceOf[NamedSymbol]); n.asInstanceOf[NamedSymbol] } else name ), f))
          case _ => Left(None)
        }
        override def preT(p: PosInExpr, e: Term): Either[Option[StopTraversal], Term] = if (e == o) Right(n) else Left(None)
      }, f) match {
        case Some(g) => g
        case None => throw new IllegalStateException("Replacing one variable by another should not fail")
      }

      def constructInstanceAndSubst(f: Formula): Option[(Formula, Substitution, (Variable, Variable))] = f match {
        case Forall(x, qf) if (x.contains(quantified)) =>
          def forall(h: Formula) = if (x.length > 1) Forall(x.filter(_ != quantified), h) else h
          // construct substitution
          val aX = Variable("x", None, Real)
          val aT = Variable("t", None, Real)
          val aP = Function("p", None, Real, Bool)
          val l = List(new SubstitutionPair(Apply(aP, aX), forall(replace(qf)(quantified, aX))),
            new SubstitutionPair(aT, instance))
          // construct axiom instance: \forall x. p(x) -> p(t)
          val g = replace(qf)(quantified, instance)
          val axiomInstance = Imply(f, forall(g))
          Some(axiomInstance, new Substitution(l), (quantified, aX))
        case _ => println("Cannot handle " + f.prettyString()); None
      }

      // since we have an implication, we use modus ponens to get it's consequence
      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
        axiom match {
          case Some(a) =>
            constructInstanceAndSubst(getFormula(node.sequent, pos)) match {
              case Some((axiomInstance, subst, (quantified, aX))) =>
                val eqPos = new Position(true, node.sequent.ante.length, HereP)
                val branch1Tactic = modusPonensT(pos, eqPos) & (hideT(eqPos) & hideT(pos))
                val hideAllAnte = for (i <- node.sequent.ante.length - 1 to 0 by -1) yield hideT(new Position(true, i))
                // this will hide all the formulas in the current succedent (the only remaining one will be the one we cut in)
                val hideAllSuccButLast = for (i <- node.sequent.succ.length - 1 to 0 by -1) yield hideT(new Position(false, i))
                def alpha(p: Position, q: Variable) = (new ApplyRule(new AlphaConversion(p, q.name, q.index, "$" + aX.name, aX.index)) {
                  override def applicable(node: ProofNode): Boolean = true
                } ~ hideT(new Position(p.isAnte, p.getIndex)))
                def repl(f: Formula, v: Variable) = f match {
                  case Imply (a, b) => Imply(replace (a) (v, Variable ("$" + aX.name, aX.index, aX.sort) ), b)
                  case _ => throw new IllegalArgumentException("...")
                }
                val replMap = Map(repl(axiomInstance, quantified) -> repl(a, aX))
                println("====== " + replMap)
                val branch2Tactic = (((hideAllAnte ++ hideAllSuccButLast).reduce(seqT)) ~
                  alpha(new Position(false, 0, new PosInExpr().first), quantified) ~
                  (uniformSubstT(subst, replMap) &
                    (axiomT(axiomName) ~ alpha(new Position(true, 0, new PosInExpr().first), aX) & AxiomCloseT)))
                Some(cutT(axiomInstance) &(branch1Tactic, branch2Tactic))
              case None => println("Giving up " + this.name); None
            }
          case None => println("Giving up because the axiom does not exist " + this.name); None
        }

    }
  }

  // [++] choice
  // I induction

  /**
   * Tactic that executes "correct" tactic based on top-level operator
   */
  def indecisive(beta: Boolean, simplifyProg: Boolean, equiv: Boolean = false): PositionTactic = new PositionTactic("Indecisive") {
    override def applies(s: Sequent, p: Position): Boolean = getTactic(s, p).isDefined

    def getTactic(s: Sequent, p: Position) = {
      val f = getFormula(s, p)
      val res = f match {
        case Not(_) => if(p.isAnte) Some(NotLeftT(p)) else Some(NotRightT(p))
        case And(_, _) => if(p.isAnte) Some(AndLeftT(p)) else if(beta) Some(AndRightT(p)) else None
        case Or(_, _) => if(p.isAnte) if(beta) Some(OrLeftT(p)) else None else Some(OrRightT(p))
        case Imply(_, _) => if(p.isAnte) if(beta) Some(ImplyLeftT(p)) else None else Some(ImplyRightT(p))
        case Equiv(_, _) => if(equiv) if(p.isAnte) Some(EquivLeftT(p)) else Some(EquivRightT(p)) else None
        case BoxModality(prog, f) if(simplifyProg) => prog match {
          case Sequence(_, _) => Some(boxSeqT(p))
          case Assign(_, _) => Some(assignment(p))
          case Test(_) => Some(boxTestT(p))
          case _ => None
        }
        case _ => None
      }
      println("applicable to " + f + " is " + res)
      res
    }

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = getTactic(node.sequent, p)
    }
  }

}
