package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.controllers.{AuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.dto.v0.{InputTask, OutputTask}
import org.thp.thehive.models._
import org.thp.thehive.services.LocalUserSrv
import play.api.libs.json.{JsObject, Json}
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.{Configuration, Environment}

class TaskCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv          = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration = Configuration.load(Environment.simple())

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider ⇒
    val app: AppBuilder = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bind[AuthenticateSrv, TestAuthenticateSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val taskCtrl: TaskCtrl              = app.instanceOf[TaskCtrl]
    implicit lazy val mat: Materializer = app.instanceOf[Materializer]

    s"[$name] task controller" should {

      "create a new task for an existing case" in {
        val request = FakeRequest("POST", "/api/case/case2/task?flag=true")
          .withJsonBody(
            Json
              .parse(
                """{
                    "title": "case 1 task",
                    "group": "group1",
                    "description": "description task 1",
                    "status": "waiting"
                }"""
              )
          )
          .withHeaders("user" → "user2")

        val result           = taskCtrl.create("case2")(request)
        val resultTask       = contentAsJson(result)
        status(result) shouldEqual 201

        val resultTaskOutput = resultTask.as[OutputTask]
        val expected = Json.toJson(
          OutputTask(
            _id = resultTaskOutput._id,
            id = resultTaskOutput.id,
            title = "case 1 task",
            description = Some("description task 1"),
            startDate = None,
            flag = true,
            status = "waiting",
            order = resultTaskOutput.order,
            group = Some("group1"),
            endDate = None,
            dueDate = None
          )
        )

        resultTask.toString shouldEqual expected.toString
      }
    }
  }
}