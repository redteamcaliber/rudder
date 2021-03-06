/*
*************************************************************************************
* Copyright 2011 Normation SAS
*************************************************************************************
*
* This file is part of Rudder.
*
* Rudder is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* In accordance with the terms of section 7 (7. Additional Terms.) of
* the GNU General Public License version 3, the copyright holders add
* the following Additional permissions:
* Notwithstanding to the terms of section 5 (5. Conveying Modified Source
* Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU General
* Public License version 3, when you create a Related Module, this
* Related Module is not considered as a part of the work and may be
* distributed under the license agreement of your choice.
* A "Related Module" means a set of sources files including their
* documentation that, without modification of the Source Code, enables
* supplementary functions or services in addition to those offered by
* the Software.
*
* Rudder is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Rudder.  If not, see <http://www.gnu.org/licenses/>.

*
*************************************************************************************
*/

package com.normation.rudder.web.services

import scala.xml.NodeSeq
import scala.xml.NodeSeq.seqToNodeSeq
import com.normation.inventory.domain.NodeId
import com.normation.rudder.domain.policies.FullGroupTarget
import com.normation.rudder.domain.policies.FullOtherTarget
import com.normation.rudder.domain.policies.FullRuleTargetInfo
import com.normation.rudder.repository.FullActiveTechniqueCategory
import com.normation.rudder.web.model.JsTreeNode
import net.liftweb.common.Loggable
import net.liftweb.http.SHtml
import net.liftweb.http.js.JsCmds._
import net.liftweb.http.js.JsCmd
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers.{ boolean2, strToSuperArrowAssoc }
import com.normation.rudder.repository.FullActiveTechniqueCategory
import com.normation.rudder.repository.FullActiveTechnique
import com.normation.rudder.domain.policies.Directive
import scala.xml.Text
import com.normation.rudder.repository.FullActiveTechnique
import com.normation.rudder.repository.FullActiveTechnique
import com.normation.rudder.repository.FullActiveTechniqueCategory
import com.normation.rudder.repository.FullActiveTechnique
import com.normation.rudder.domain.policies.Rule
import com.normation.rudder.domain.policies.DirectiveId
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.S
import com.normation.cfclerk.domain.Technique
import com.normation.rudder.web.model.JsInitContextLinkUtil._
import bootstrap.liftweb.RudderConfig
import net.liftweb.common.Full
import net.liftweb.common.EmptyBox
import com.normation.rudder.domain.policies.GlobalPolicyMode
import com.normation.rudder.domain.policies.PolicyMode._
import com.normation.rudder.domain.policies.PolicyModeOverrides._

/**
 *
 * A service that is able to render the node group tree.
 *
 */
object DisplayDirectiveTree extends Loggable {

  /**
   * Display the directive tree, optionaly filtering out
   * some category or group by defining which one to
   * keep. By default, display everything.
   */
  def displayTree(
      directiveLib    : FullActiveTechniqueCategory
    , globalMode      : GlobalPolicyMode
      //set of all directives at least used by one rule
      //the int is the number of rule which used it
    , usedDirectiveIds: Seq[(DirectiveId, Int)]
    , onClickCategory : Option[FullActiveTechniqueCategory => JsCmd]
    , onClickTechnique: Option[(FullActiveTechniqueCategory, FullActiveTechnique) => JsCmd]
    , onClickDirective: Option[(FullActiveTechniqueCategory, FullActiveTechnique, Directive) => JsCmd]
    , addEditLink     : Boolean
    , included        : Set[DirectiveId] = Set()
    , keepCategory    : FullActiveTechniqueCategory => Boolean = _ => true
    , keepTechnique   : FullActiveTechnique => Boolean = _ => true
    , keepDirective   : Directive => Boolean = _ => true
  ) : NodeSeq =  {

    def displayCategory(
        category: FullActiveTechniqueCategory
      , nodeId  : String
    ) : JsTreeNode = new JsTreeNode {

      private[this] val localOnClickTechnique = onClickTechnique.map( _.curried(category) )

      private[this] val localOnClickDirective = onClickDirective.map( _.curried(category) )

      private[this] val tooltipId = Helpers.nextFuncName
      private[this] val xml = (
        <span class="treeActiveTechniqueCategoryName tooltipable" tooltipid={tooltipId} title="">
          {Text(category.name)}
        </span>
        <div class="tooltipContent" id={tooltipId}>
          <h3>{category.name}</h3>
          <div>{category.description}</div>
        </div>
      )

      override def body = onClickCategory match {
        case None    => <a href="#">{xml}</a>
        case Some(f) => SHtml.a({() => f(category)}, xml)
      }

      // We don't want to display system Categories nor Directives
      override def children = (
        category.subCategories
          .filterNot(_.isSystem)
          .sortBy( _.name )
          .zipWithIndex
          .collect{ case (node, i) if(keepCategory(node)) =>
              displayCategory(node, nodeId + "_" + i)
          }
        ++
        category.activeTechniques
          .filterNot(_.isSystem)
          .sortBy( _.techniqueName.value )
          .collect { case at if(keepTechnique(at)) =>
            displayActiveTechnique(at, localOnClickTechnique, localOnClickDirective)
          }
      )

      override val attrs =
        ("data-jstree" -> """{ "type" : "category" }""") :: ( "id" -> nodeId) :: Nil
    }

   /////////////////////////////////////////////////////////////////////////////////////////////

    def displayActiveTechnique(
        activeTechnique: FullActiveTechnique
      , onClickTechnique: Option[FullActiveTechnique => JsCmd]
      , onClickDirective: Option[FullActiveTechnique => Directive => JsCmd]
    ) : JsTreeNode = new JsTreeNode {

      private[this] val localOnClickDirective = onClickDirective.map( f => f(activeTechnique) )

      override val attrs = (
        ("data-jstree" -> s"""{ "type" : "template" , "state" : { "disabled" : ${ !activeTechnique.isEnabled} } }""") :: Nil
      )

      override def children = {
        activeTechnique.directives
          .sortBy( _.name )
          .collect {
            case x if( keepDirective(x) ) =>
              displayDirective(
                  x
                , activeTechnique.techniques.get(x.techniqueVersion)
                , localOnClickDirective
              )
          }
      }

      override def body = {
        val tooltipId = Helpers.nextFuncName

        //display information (name, etc) relative to last technique version

        val xml = activeTechnique.newestAvailableTechnique match {
          case Some(technique) =>
            <span class="treeActiveTechniqueName tooltipable" tooltipid={tooltipId} title="">{technique.name}
            </span>
            <div class="tooltipContent" id={tooltipId}>
              <h3>{technique.name}</h3>
              <div>{technique.description}</div>
            </div>
          case None =>
            <span class="error">The technique with id ''{activeTechnique.techniqueName}'' is missing from repository</span>
        }

        onClickTechnique match {
          case None                           => <a style="cursor:default">{xml}</a>
          case _ if(activeTechnique.isSystem) => <a style="cursor:default">{xml}</a>
          case Some(f)                        => SHtml.a(() => f(activeTechnique), xml)
        }
      }
    }

    def displayDirective(
        directive        : Directive
      , technique        : Option[Technique]
      , onClickDirective : Option[Directive => JsCmd]
    ) : JsTreeNode = new JsTreeNode {

      private[this] val configService  = RudderConfig.configService

      val isAssignedTo = usedDirectiveIds.find{ case(id,_) => id == directive.id }.map(_._2).getOrElse(0)

      override def children = Nil

      val classes = {
        val includedClass = if (included.contains(directive.id)) {"included"} else ""
        val disabled = if(directive.isEnabled) "" else "disableTreeNode"
        s"${disabled} ${includedClass}"
      }
      val htmlId = s"jsTree-${directive.id.value}"
      override val attrs = (
                  ("data-jstree" -> """{ "type" : "directive" }""") ::
                  ( "id" -> htmlId) ::
                  ("class" -> classes ) ::
                  Nil
      )

      override def body = {

        val editButton = {
          if (addEditLink && ! directive.isSystem) {
            val tooltipId = Helpers.nextFuncName
            <span class="treeActions">
              <span  class="tooltipable treeAction noRight directiveDetails fa fa-pencil" tooltipid={tooltipId} title="" onclick={redirectToDirectiveLink(directive.id).toJsCmd}> </span>
						  <div class="tooltipContent" id={tooltipId}><div>Configure this Directive.</div></div>
						</span>
          } else {
            NodeSeq.Empty
          }
        }

        val deprecated = technique.flatMap(_.deprecrationInfo) match {
          case Some(info) =>
            val tooltipId = Helpers.nextFuncName
            <span class="fa fa-exclamation text-danger deprecatedTechniqueIcon tooltipable" style="padding-left:5px" tooltipid={tooltipId} title=""></span>
            <div class="tooltipContent" id={tooltipId}>
              <div>Deprecated: {info.message}</div>
            </div>
          case None => NodeSeq.Empty
        }

        val xml  = {
          val (policyMode,explanation) =
              (globalMode.overridable,directive.policyMode) match {
                case (Always,Some(mode)) =>
                  (mode,"<p>This mode is an override applied to this directive. You can change it in the <i><b>directive's settings</b></i>.</p>")
                case (Always,None) =>
                  val expl = """<p>This mode is the globally defined default. You can change it in <i><b>settings</b></i>.</p><p>You can also override it on this directive in the <i><b>directive's settings</b></i>.</p>"""
                  (globalMode.mode, expl)
                case (Unoverridable,_) =>
                  (globalMode.mode, "<p>This mode is the globally defined default. You can change it in <i><b>Settings</b></i>.</p>")
              }

          val tooltipId = Helpers.nextFuncName
          <span class="treeDirective tooltipable" tooltipid={tooltipId} title="" ><span id={"badge-apm-"+tooltipId}>[BADGE]</span>[{directive.techniqueVersion.toString}] {directive.name}
          {

              if(isAssignedTo <= 0) {
                <span style="padding-left:5px" class="fa fa-warning text-warning-rudder"></span>
              } else {
                NodeSeq.Empty
              }
          }
          </span>  ++
          Script(JsRaw(s"""$$('#badge-apm-${tooltipId}').replaceWith(createBadgeAgentPolicyMode('directive',"${policyMode}", "${explanation.toString()}"))""")) ++
          deprecated ++
          editButton ++
          <div class="tooltipContent" id={tooltipId}>
            <h3>{directive.name}</h3>
            <div>{directive.shortDescription}</div>
            <div>Technique version: {directive.techniqueVersion.toString}</div>
            <div>{s"Used in ${isAssignedTo} rules" }</div>
              { if(!directive.isEnabled) <div>Disable</div> }
            </div>

        }

        onClickDirective match {
          case None                     => <a style="cursor:default">{xml}</a>
          case _ if(directive.isSystem) => <a style="cursor:default">{xml}</a>
          case Some(f)                  => SHtml.a(() => f(directive), xml)
        }
      }
    }

    displayCategory(directiveLib, "jstn_0").toXml
  }

}
