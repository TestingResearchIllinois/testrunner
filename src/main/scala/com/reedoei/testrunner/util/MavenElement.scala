package com.reedoei.testrunner.util

import java.nio.file.Path

import com.reedoei.eunomia.util.Util

import scala.collection.JavaConverters._
import org.dom4j.Element

case class MavenElement(coordinates: Coordinates, configuration: Option[Element])

object MavenElement {
    def elements(path: Path, containerName: String, elemName: String): List[MavenElement] =
        Util.readXmlDoc(path.toFile).getRootElement
            .element("build").element(containerName).elements(elemName)
            .asScala.toList.map(fromXml)

    def fromXml(element: Element): MavenElement = MavenElement(Coordinates(
        element.elementText("groupId"),
        element.elementText("artifactId"),
        element.elementText("version")),
        element.elements("configuration").asScala.headOption)
}
