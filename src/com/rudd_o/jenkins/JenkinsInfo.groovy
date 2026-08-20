package com.rudd_o.jenkins

import hudson.model.ParametersAction

class JenkinsInfo implements Serializable {

  JenkinsInfo() {
  }

  @NonCPS
  String[] getFedoraPipelineMasterBranchNames() {
    Jenkins.instance.getAllItems(org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject).collect { multibranchpipeline ->
      multibranchpipeline.getItems().collect { branch ->
        branch.getAllJobs().findAll({it.name == "master"}).findAll({ it.isBuildable() }).collect { job ->
          def lastbuild = job.getLastSuccessfulBuild()
          if (!lastbuild) {
            lastbuild = job.getLastStableBuild()
          }
          if (!lastbuild) {
            lastbuild = job.getLastBuild()
          }
          [job, lastbuild]
        }.findAll({ it[1] }).findAll({ 
          def paction = it[1].getActions(ParametersAction)[0]
          if (paction) {
            paction.getParameter("FEDORA_RELEASES")
          } else {
            null
          }
        }).collect {
          it[0].getFullName()
        }
      }
    }.flatten()
  }

}