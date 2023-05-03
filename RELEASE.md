# CzechIdM release process

Bellow are the steps needed to perform when releasing a new version of CzechIdM. These are the same regardless if this a major, minor, or RC release.

1. Ensure that all code is in the develop branch. No open PRs to develop which are meant to be included in the release remain open.
2. In a separate branch, set the version in each pom.xml and package.json. Set Docker image versions in Dockerfile, docker-compose-czechidm.yml, and docker-compose-czechidm-H2.yml. See more about Docker image versioning [here](Docker/images/czechidm/). Merge this branch to develop via a PR.
3. Merge the develop branch into the master branch via a PR.
4. Create Github release and tag.
5. Start the Jenkins release job.
6. Download the built artifact from Nexus and build the CzechIdM Docker image. Upload the Docker image to the development repository.
   ```
   docker build -t bcv-czechidm:<czechidm-image-version> ./
   docker tag bcv-czechidm:<czechidm-image-version> <devel-test-repository-url>:<devel-repository-port>/bcv-czechidm:<czechidm-image-version>
   docker login <devel-repository-url>:<devel-repository-port>
   docker image push <devel-repository-url>:<devel-repository-port>/bcv-czechidm:<czechidm-image-version>
   ```
7. Deploy the image in a testing appliance environment. Ensure that it starts without an issue.
8. Upload the Docker image to the production repository.
   ```
   docker build -t bcv-czechidm:<czechidm-image-version> ./
   docker tag bcv-czechidm:<czechidm-image-version> <prod-test-repository-url>:<prod-repository-port>/bcv-czechidm:<czechidm-image-version>
   docker login <prod-repository-url>:<prod-repository-port>
   docker image push <prod-repository-url>:<prod-repository-port>/bcv-czechidm:<czechidm-image-version>
   ```
9. In a separate branch, set the version in each pom.xml and package.json to SNAPSHOT again. Leave the Docker versions as they are. Merge this branch to develop via a PR.