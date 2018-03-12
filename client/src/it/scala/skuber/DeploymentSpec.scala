package skuber

import org.scalatest.Matchers
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import skuber.ext.Deployment
import skuber.json.ext.format._
import scala.concurrent.duration._

import scala.concurrent.Future

class DeploymentSpec extends K8SFixture with Eventually with Matchers {
  val nginxDeploymentName: String = java.util.UUID.randomUUID().toString

  behavior of "Deployment"

  it should "create a deployment" in { k8s =>
    k8s.create(getNginxDeployment(nginxDeploymentName, "1.7.9")) map { d =>
      assert(d.name == nginxDeploymentName)
    }
  }

  it should "get the newly created deployment" in { k8s =>
    k8s.get[Deployment](nginxDeploymentName) map { d =>
      assert(d.name == nginxDeploymentName)
    }
  }

  it should "upgrade the newly created deployment" in { k8s =>
    k8s.get[Deployment](nginxDeploymentName).flatMap { d =>
      val updatedDeployment = d.updateContainer(getNginxContainer("1.9.1"))
      k8s.update(updatedDeployment).flatMap { _ =>
        eventually(timeout(15 seconds), interval(15 seconds)) {
          k8s.get[Deployment](nginxDeploymentName).map { ud =>
            ud.status.fold(fail) { s =>
              s.updatedReplicas shouldBe 1
            }
          }
        }
      }
    }
  }

  it should "delete a deployment" in { k8s =>
    k8s.deleteWithOptions[Deployment](nginxDeploymentName, DeleteOptions(propagationPolicy = Some(DeletePropagation.Foreground))).map { _ =>
      eventually(timeout(300 seconds), interval(3 seconds)) {
        val f: Future[Deployment] = k8s.get[Deployment](nginxDeploymentName)
        ScalaFutures.whenReady(f.failed) { e =>
          e shouldBe a[K8SException]
        }
      }
    }
  }

  def getNginxContainer(version: String): Container = Container(name = "nginx", image = "nginx:" + version).exposePort(80)

  def getNginxDeployment(name: String, version: String): Deployment = {
    val nginxContainer = getNginxContainer(version)
    val nginxTemplate = Pod.Template.Spec.named("nginx").addContainer(nginxContainer).addLabel("app" -> "nginx")
    Deployment(name).withTemplate(nginxTemplate)
  }
}