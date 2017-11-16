package bloop
package tasks

import utest._

import scala.concurrent.ExecutionContext.Implicits.global

import CompilationHelpers._
import ProjectHelpers._

object CompilationTaskTest extends TestSuite {
  val tests = Tests {
    "compile an empty project" - {
      val projectStructures =
        Map("empty" -> Map.empty[String, String])

      val dependencies = Map.empty[String, Set[String]]

      withProjects(projectStructures, dependencies) { projects =>
        val project = projects("empty")

        assert(!project.previousResult.analysis.isPresent)
        assert(!project.previousResult.setup.isPresent)

        val newProjects = CompilationTask.parallel(project, projects, compilerCache, QuietLogger)
        val newProject  = newProjects("empty")

        assert(newProject.previousResult.analysis.isPresent)
        assert(newProject.previousResult.setup.isPresent)
      }
    }

    "Compile a project with a single correct source" - {
      val projectStructures =
        Map("prj" -> Map("A.scala" -> "object A"))

      val dependencies = Map.empty[String, Set[String]]

      withProjects(projectStructures, dependencies) { projects =>
        val project = projects("prj")

        assert(!project.previousResult.analysis.isPresent)
        assert(!project.previousResult.setup.isPresent)

        val newProjects = CompilationTask.parallel(project, projects, compilerCache, QuietLogger)
        val newProject  = newProjects("prj")

        assert(newProject.previousResult.analysis.isPresent)
        assert(newProject.previousResult.setup.isPresent)
      }
    }

    "Compile two projects with a dependency" - {
      val projectStructures =
        Map(
          "parent" -> Map("A.scala" -> """package p0
                                         |class A""".stripMargin),
          "child"  -> Map("B.scala" -> """package p1
                                        |import p0.A
                                        |class B extends A""".stripMargin)
        )

      val dependencies = Map("child" -> Set("parent"))

      withProjects(projectStructures, dependencies) { projects =>
        assert(projects.forall { case (_, prj) => !prj.previousResult.analysis.isPresent })
        assert(projects.forall { case (_, prj) => !prj.previousResult.setup.isPresent })

        val project     = projects("child")
        val newProjects = CompilationTask.parallel(project, projects, compilerCache, ConsoleLogger)

        assert(newProjects.forall { case (_, prj) => prj.previousResult.analysis.isPresent })
        assert(newProjects.forall { case (_, prj) => prj.previousResult.setup.isPresent })
      }

    }

    "Compile one project with two dependencies" - {
      val projectStructures =
        Map(
          "parent0" -> Map("A.scala" -> """package p0
                                          |trait A""".stripMargin),
          "parent1" -> Map("B.scala" -> """package p1
                                          |trait B""".stripMargin),
          "child"   -> Map("C.scala" -> """package p2
                                        |import p0.A
                                        |import p1.B
                                        |object C extends A with B""".stripMargin)
        )

      val dependencies = Map("child" -> Set("parent0", "parent1"))

      withProjects(projectStructures, dependencies) { projects =>
        assert(projects.forall { case (_, prj) => !prj.previousResult.analysis.isPresent })
        assert(projects.forall { case (_, prj) => !prj.previousResult.setup.isPresent })

        val child       = projects("child")
        val newProjects = CompilationTask.parallel(child, projects, compilerCache, QuietLogger)

        assert(newProjects.forall { case (_, prj) => prj.previousResult.analysis.isPresent })
        assert(newProjects.forall { case (_, prj) => prj.previousResult.setup.isPresent })
      }
    }

    "Un-necessary projects are not compiled" - {
      val projectStructures =
        Map(
          "parent"    -> Map("A.scala" -> """package p0
                                         |trait A""".stripMargin),
          "unrelated" -> Map("B.scala" -> """package p1
                                            |trait B""".stripMargin),
          "child"     -> Map("C.scala" -> """package p2
                                        |import p0.A
                                        |object C extends A""".stripMargin)
        )

      val dependencies = Map("child" -> Set("parent"))

      withProjects(projectStructures, dependencies) { projects =>
        assert(projects.forall { case (_, prj) => !prj.previousResult.analysis.isPresent })
        assert(projects.forall { case (_, prj) => !prj.previousResult.setup.isPresent })

        val child       = projects("child")
        val newProjects = CompilationTask.parallel(child, projects, compilerCache, QuietLogger)

        // The unrelated project should not have been compiled
        assert(!newProjects("unrelated").previousResult.analysis.isPresent)
        assert(!newProjects("unrelated").previousResult.setup.isPresent)

        assert(newProjects("parent").previousResult.analysis.isPresent)
        assert(newProjects("parent").previousResult.setup.isPresent)
        assert(newProjects("child").previousResult.analysis.isPresent)
        assert(newProjects("child").previousResult.setup.isPresent)
      }
    }
  }
}
