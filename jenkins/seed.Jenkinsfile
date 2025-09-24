#!/usr/bin/env groovy

def config
pipeline {
    agent any
	parameters {
        string(name: 'RESPONSIBLE_USER', defaultValue: '', description: 'Responsible user for bygging. Se https://atlas-docs.atlas.vegvesen.no/atlas-dokumentasjon/latest/client/byggserver.html#_ansvarlig_bruker')
    }
	stages {
		stage('Seed'){
			steps {
				script {config = readYaml file:'jenkins/config.yml'}
				mono rootFolder: 'nvdb-tnits-public', credentialsId: config.credentials.git, view: 'TN-ITS Eksport'
			}
		}
	}
}
