
def AdjustBuildURL(String buildurl = 'default', String endpointinfo = 'default'){
    echo "this is AdjustBuildURL Groovy script function..."
    echo "this is buildurl: ${buildurl}"
    echo "this is endpointinfo: ${endpointinfo}"
    String localHost = "localhost"
    def result = ""
    if (buildurl.contains(localHost)) {
        echo "hits if conditional, buildurl does contain localhost"
        String actualbuildurl = buildurl.replaceAll(localHost, endpointinfo)
        result = actualbuildurl
        echo "this is result that will be returned: ${result}"
        return result
    } else {
        echo "hits else block, buildurl does not contain localhost"
        result = buildurl
        echo "this is result that will be returned: ${result}"
        return result
    }
}
boolean PROVISIONINGPASSED = false
boolean DOCKERIZEDTESTREPOBUILDPASSED = false
boolean DOCKERIZEDAPITESTSPASSED = false
String DOCKERFAILUREAPI = ""
def DOCKERRESULT = ""
pipeline {
    agent any
    options{
        timeout(time: 14, unit: 'HOURS')
        disableConcurrentBuilds()
    }
    environment{
        SLACK_CREDENTIALS = credentials('jenkins-slack-token')
        JENKINS_ENDPOINT_IP = credentials('jenkins-endpoint-ip')
    }
    stages {
        // stage('tell Slack we are starting the pipeline'){
        //     steps{
        //         script{
        //             if(params.slack_notify){
        //                 String urlToUse = AdjustBuildURL("${BUILD_URL}", "${JENKINS_ENDPOINT_IP_PSW}")
        //                 echo 'sending message to slack, letting channel know build has been started...'
        //                 sh """curl --request POST --url https://hooks.slack.com/services/${SLACK_CREDENTIALS_PSW} --header "Content-Type: application/json; charset=utf-8" --data '{"user": "Harvester Jenkins", "as_user": "true","channel": "#proj-harvester-build", "blocks": [{ "type": "section", "text": { "type": "mrkdwn", "text": "Jenkins-Vagrant-AirGap - *status:* *_starting_*" } }, { "type": "divider" }, { "type": "section", "text": { "type": "mrkdwn", "text": "$urlToUse , first-stage, running... " } } ]}'"""
        //             } else {
        //                 echo 'slack notifications currently turned off...'
        //             }
        //         }
        //     }
        // }
        stage('checkout harvester-installer master branch (temporarily set to featurebranch)') {
            steps {
                dir('harvester-installer') {
                    checkout([$class: 'GitSCM', branches: [[name: 'feat/airgap-harvester-airgap-rancher-install-test' ]], userRemoteConfigs: [[url: 'https://github.com/irishgordo/harvester-installer.git']]])
                }
                script{
                    if(params.slack_notify){
                        String urlToUse = AdjustBuildURL("${BUILD_URL}", "${JENKINS_ENDPOINT_IP_PSW}")
                        echo 'sending message to slack, letting channel know build has grabbed latest from master branch...'
                        sh """curl --request POST --url https://hooks.slack.com/services/${SLACK_CREDENTIALS_PSW} --header "Content-Type: application/json; charset=utf-8" --data '{"user": "Harvester Jenkins", "as_user": "true","channel": "#proj-harvester-build","blocks": [{ "type": "section", "text": { "type": "mrkdwn", "text": "Jenkins-Vagrant-AirGap - *status:* *_started_*" } }, { "type": "divider" }, { "type": "section", "text": { "type": "mrkdwn", "text": "$urlToUse , grabbed latest master branch changes... " } } ]}'"""
                    } else {
                        echo 'slack notifications currently turned off...'
                    }
                }
            }
        }
        stage('checkout harvester/tests branch clone down (temporarily set to featurebranch)') {
            steps {
                script{
                    echo 'cloning down tests repo (currently fork)...'
                    dir("${params['WORKSPACE']}"){
                        sh "git clone https://github.com/irishgordo/tests.git"
                        echo 'cloned tests repo successfully (currently fork)...'
                        dir('tests'){
                            sh "git checkout feat/dockerize-backend-tests"
                            def baseSettingsYaml = readYaml file: 'config.yml'
                            sh "echo 'initial YAML Settings that will be parsed and overwritten:'"
                            print baseSettingsYaml
                            baseSettingsYaml.endpoint = "https://" + params.harvester_vip_ip
                            baseSettingsYaml.username = params.harvester_admin_user
                            baseSettingsYaml.password = params.harvester_admin_password
                            baseSettingsYaml.harvester_cluster_nodes = params.harvester_cluster_nodes.trim() as Integer
                            baseSettingsYaml['wait-timeout'] = params.wait_timeout.trim() as Integer
                            sh "echo 'modified YAML Settings that will be written to config.yml for tests:'"
                            print baseSettingsYaml
                            def newBaseSettingsYaml = writeYaml file: 'config.yml', data: baseSettingsYaml, overwrite: true
                            sh "make build-docker-backend-tests-image"
                        }
                        DOCKERIZEDTESTREPOBUILDPASSED = true
                    }
                    if(params.slack_notify){
                        echo 'sending message to slack, letting channel know jenkins job about dockerized tests repo...'
                        if(DOCKERIZEDTESTREPOBUILDPASSED){
                            String urlToUse = AdjustBuildURL("${BUILD_URL}", "${JENKINS_ENDPOINT_IP_PSW}")
                            sh """curl --request POST --url https://hooks.slack.com/services/${SLACK_CREDENTIALS_PSW} --header "Content-Type: application/json; charset=utf-8" --data '{"user": "Harvester Jenkins", "as_user": "true","channel": "#proj-harvester-build","blocks": [{ "type": "section", "text": { "type": "mrkdwn", "text": "Jenkins-Vagrant-Airgap - *status:* *_started_*" } }, { "type": "divider" }, { "type": "section", "text": { "type": "mrkdwn", "text": "$urlToUse , pulled harvester/tests and built harvester/tests docker image: *SUCCESS* " } } ]}'"""
                        } else {
                            String urlToUse = AdjustBuildURL("${BUILD_URL}", "${JENKINS_ENDPOINT_IP_PSW}")
                            sh """curl --request POST --url https://hooks.slack.com/services/${SLACK_CREDENTIALS_PSW} --header "Content-Type: application/json; charset=utf-8" --data '{"user": "Harvester Jenkins", "as_user": "true","channel": "#proj-harvester-build","blocks": [{ "type": "section", "text": { "type": "mrkdwn", "text": "Jenkins-Vagrant-AirGap - *status:* *_started_*" } }, { "type": "divider" }, { "type": "section", "text": { "type": "mrkdwn", "text": "$urlToUse , pulled harvester/tests and built harvester/tests docker image: *FAIULRE* " } } ]}'"""
                        }
                    } else {
                        echo 'slack notifications currently turned off...'
                    }
                }
            }
        }
        stage('temp: add upstream repo and pull changes'){
            steps{
                dir('harvester-installer'){
                    script{
                        echo 'temporarily pulling upstream harvester-installer - demoing test environment...'
                        try {
                            def addUpstream = sh(script: "git remote add upstream https://github.com/harvester/harvester-installer.git", returnStdout: true).trim()
                        } catch (Exception e) {
                            echo 'Exception occurred: ' + e.toString()
                        }
                        def fetchUpstream = sh(script: "git fetch upstream", returnStdout: true).trim()
                        def fetchRemotes = sh(script: "git remote -v", returnStdout: true).trim()
                        echo 'now we will grab harvester master branch, switch to the master branch on fork, pull down master branch from upstream harvester, then checkout the fork feature branch and merge the origin master that has been updated within it to keep the run up to date...'
                        def pullUpstreamMaster = sh(script: "git checkout origin/master && git pull upstream master && git checkout feat/airgap-harvester-airgap-rancher-install-test && git merge origin/master", returnStdout: true).trim()
                    }
                }
            }
        }
        // stage('Provision an instance of Airgapped Rancher (k3s) with 4 Node Airgapped Harvester') {
        //     steps {
        //         script {
        //             if(params.slack_notify){
        //                 echo 'sending message to slack, letting channel know jenkins job about starting provisioning...'
        //                 String urlToUse = AdjustBuildURL("${BUILD_URL}", "${JENKINS_ENDPOINT_IP_PSW}")
        //                 sh """curl --request POST --url https://hooks.slack.com/services/${SLACK_CREDENTIALS_PSW} --header "Content-Type: application/json; charset=utf-8" --data '{"user": "Harvester Jenkins", "as_user": "true","channel": "#proj-harvester-build","blocks": [{ "type": "section", "text": { "type": "mrkdwn", "text": "Jenkins-Vagrant-Airgap - *status:* *_started_*" } }, { "type": "divider" }, { "type": "section", "text": { "type": "mrkdwn", "text": "$urlToUse , 6ish-hr long provisioning process for 4 Airgapped Harvester Nodes, 1 Airgapped Rancher (on k3s) with Docker-Registry has: *STARTED* " } } ]}'"""
        //             } else {
        //                 echo 'slack notifications currently turned off...'
        //             }
        //             ansiblePlaybook extras: "-e WORKSPACE=${env.WORKSPACE} -e harvester_installer_repo_name=irishgordo/harvester-installer", playbook: "${env.WORKSPACE}/harvester-installer/ci/run_airgap_rancher_and_airgap_harvester_install_test.yml"
        //             PROVISIONINGPASSED = true
        //         }
        //     }
        // }
        stage('Run dockerized harvester/tests API tests against 4 Node Airgapped Harvester'){
            options {
                timeout(time: 1, unit: "HOURS")
            }
            steps {
                dir('tests'){
                    script {
                        try {
                            docker.image('harvester-tests-backend:latest').inside("""-v $WORKSPACE/tests:/test-output --name backend-tests-api --add-host ${params.rancher_install_domain}:${params.rancher_node_ip} --add-host ${params.rancher_node_registry_domain}:${params.rancher_node_ip}""") {
                                DOCKERRESULT = sh(returnStdout: true, script: """pytest -v -k 'test_get_host' --junitxml="/test-output/result_harvester_api_latest.xml" --wait-timeout=5 --sleep-timeout=1 -m 'not delete_host' harvester_e2e_tests/apis""").trim()
                                //DOCKERRESULT = sh(returnStdout: true, script: """/usr/local/bin/python3 -m pytest -v -k 'test_get_host' --junitxml="/test-output/result_harvester_api_latest.xml" --wait-timeout=5 --sleep-timeout=1 -m 'not delete_host' harvester_e2e_tests/apis""").trim()
                            }
                            //DOCKERRESULT = sh(returnStdout: true, script: """docker run --name backend-tests-api -v $WORKSPACE/tests:/test-output -t --add-host ${params.rancher_install_domain}:${params.rancher_node_ip} --add-host ${params.rancher_node_registry_domain}:${params.rancher_node_ip} --rm harvester-tests-backend:latest pytest -v -k 'test_get_host' --junitxml="/test-output/result_harvester_api_latest.xml" --wait-timeout=5 --sleep-timeout=1 -m 'not delete_host' harvester_e2e_tests/apis""").trim()
                            //sh """docker run --name backend-tests-api -v $WORKSPACE/tests:/test-output -t --add-host ${params.rancher_install_domain}:${params.rancher_node_ip} --add-host ${params.rancher_node_registry_domain}:${params.rancher_node_ip} --rm harvester-tests-backend:latest pytest -v -k 'not verify_host_maintenance_mode and not host_mgmt_maintenance_mode and not host_reboot_maintenance_mode and not host_poweroff_state and not create_images_using_terraform and not create_keypairs_using_terraform and not create_edit_network and not create_network_using_terraform and not create_volume_using_terraform and not test_create_with_invalid_url and not test_maintenance_mode' --junitxml="/test-output/result_harvester_api_latest.xml" -m 'not delete_host' harvester_e2e_tests/apis"""
                            DOCKERIZEDAPITESTSPASSED = true
                        } catch (err) {
                            echo "failed within docker image inside command on harvester testing api stage"
                            DOCKERFAILUREAPI = err.getMessage()
                            echo err.getMessage()
                        }
                    }
                }
            }
        }
    }
    post {
        always {
            dir("${env.WORKSPACE}"){
                script{
                    if(PROVISIONINGPASSED){
                        if(params.slack_notify){
                            String urlToUse = AdjustBuildURL("${BUILD_URL}", "${JENKINS_ENDPOINT_IP_PSW}")
                            echo 'sending message to slack, letting channel know provisioning passed...'
                            sh """curl --request POST --url https://hooks.slack.com/services/${SLACK_CREDENTIALS_PSW} --header "Content-Type: application/json; charset=utf-8" --data '{"user": "Harvester Jenkins","as_user": "true","channel": "#proj-harvester-build","blocks": [ { "type": "section", "text": { "type": "mrkdwn", "text": "Jenkins-Vagrant-AirGap - *status:* *_finished_*" } }, { "type": "divider" }, { "type": "section", "text": { "type": "mrkdwn", "text": "$urlToUse , provisioning airgap-rancher & airgap-harvester: *SUCCESS* " } } ]}'"""
                        } else {
                            echo 'slack notifications currently turned off...'
                        }
                    } else {
                        if(params.slack_notify){
                            String urlToUse = AdjustBuildURL("${BUILD_URL}", "${JENKINS_ENDPOINT_IP_PSW}")
                            echo 'sending message to slack, letting channel know provisioning failed...'
                            sh """curl --request POST --url https://hooks.slack.com/services/${SLACK_CREDENTIALS_PSW} --header "Content-Type: application/json; charset=utf-8" --data '{"user": "Harvester Jenkins","as_user": "true","channel": "#proj-harvester-build","blocks": [ { "type": "section", "text": { "type": "mrkdwn", "text": "Jenkins-Vagrant-AirGap - *status:* *_finished_*" } }, { "type": "divider" }, { "type": "section", "text": { "type": "mrkdwn", "text": "$urlToUse , provisioning airgap-rancher & airgap-harvester: *FAIULRE* " } } ]}'"""
                        } else {
                            echo 'slack notifications currently turned off...'
                        }
                    }
                    if(DOCKERIZEDAPITESTSPASSED){
                        if(params.slack_notify){
                            String urlToUse = AdjustBuildURL("${BUILD_URL}", "${JENKINS_ENDPOINT_IP_PSW}")
                            echo 'sending message to slack, letting channel know provisioning passed...'
                            sh """curl --request POST --url https://hooks.slack.com/services/${SLACK_CREDENTIALS_PSW} --header "Content-Type: application/json; charset=utf-8" --data '{"user": "Harvester Jenkins","as_user": "true","channel": "#proj-harvester-build","blocks": [ { "type": "section", "text": { "type": "mrkdwn", "text": "Jenkins-Vagrant-AirGap - *status:* *_finished_*" } }, { "type": "divider" }, { "type": "section", "text": { "type": "mrkdwn", "text": "$urlToUse ,TEST API STAGE: *SUCCESS* - $DOCKERRESULT  " } }, { "type": "divider" }, { "type": "section", "text": { "type": "mrkdwn", "text": "$DOCKERRESULT" } } ]}'"""
                        } else {
                            echo 'slack notifications currently turned off...'
                        }
                    } else {
                        if(params.slack_notify){
                            String urlToUse = AdjustBuildURL("${BUILD_URL}", "${JENKINS_ENDPOINT_IP_PSW}")
                            echo 'sending message to slack, letting channel know provisioning failed...'
                            sh """curl --request POST --url https://hooks.slack.com/services/${SLACK_CREDENTIALS_PSW} --header "Content-Type: application/json; charset=utf-8" --data '{"user": "Harvester Jenkins","as_user": "true","channel": "#proj-harvester-build","blocks": [ { "type": "section", "text": { "type": "mrkdwn", "text": "Jenkins-Vagrant-AirGap - *status:* *_finished_*" } }, { "type": "divider" }, { "type": "section", "text": { "type": "mrkdwn", "text": "$urlToUse ,TEST API STAGE: *FAIULRE* -$DOCKERRESULT" } }, { "type": "divider" }, { "type": "section", "text": { "type": "mrkdwn", "text": "$DOCKERRESULT" } } ]}'"""
                        } else {
                            echo 'slack notifications currently turned off...'
                        }
                    }
                    if(params.keep_environment){
                        echo 'keeping environment...'
                    } else {
                        echo 'discarding environment...'
                        sh "cd ipxe-examples/vagrant-pxe-airgap-harvester/ && ls -alh && vagrant destroy -f"
                    }
                    echo 'destroying docker image, so next run will re-make with fresh start...'
                    dir('tests'){
                        if(fileExists("/")){
                            if(fileExists("Makefile")){
                                try{
                                    sh "make destroy-docker-backend-tests-image"
                                } catch (err) {
                                    echo err.getMessage()
                                }
                            }
                            try {
                                junit '*.xml'
                            } catch (err) {
                                echo 'could not find any xml pytest files...'
                                echo err.getMessage()
                            }
                        } else {
                            echo 'nothing here...'
                        }
                    }
                    echo 'destroying tests folder, so next run will always get the latest...'
                    try {
                        sh "cd ${env.WORKSPACE} && rm -rf tests"
                    } catch (err) {
                        echo 'issue removing tests folder in workspace...'
                        echo err.getMessage()
                    }
                    echo 'kill running backend-tests-api container...'
                    try {
                        sh "docker kill backend-tests-api"
                    } catch(err) {
                        echo 'no backend-tests-api to kill..'
                        echo err.getMessage()
                    }
                }
            }
        }
    }
}
                
