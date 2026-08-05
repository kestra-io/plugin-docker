#Generate credentials
mkdir auth
docker run --rm \
    --entrypoint htpasswd \
    registry:2.7.0 -Bbn testuser testpassword > auth/htpasswd

#Generate private docker registry
docker run -d \
    -p 5000:5000 \
    --name plugin-docker-registry \
    -v "$(pwd)"/auth:/auth \
    -e "REGISTRY_AUTH=htpasswd" \
    -e "REGISTRY_AUTH_HTPASSWD_REALM=Registry Realm" \
    -e REGISTRY_AUTH_HTPASSWD_PATH=/auth/htpasswd \
    registry:2.7.0

#Pull image, tag it, then push it to private registry
docker pull ubuntu:20.04
docker login localhost:5000 -u testuser -p testpassword
docker tag ubuntu:20.04 localhost:5000/ubuntu:unit-test
docker push localhost:5000/ubuntu:unit-test

#Logout
docker rmi ubuntu:20.04
docker logout localhost:5000

#Install Docker Model Runner for the io.kestra.plugin.docker.model IT tests
sudo apt-get update
sudo apt-get install -y docker-model-plugin

#Start the model runner on TCP 12434 (default port on Docker Engine)
docker model install-runner --port 12434

#Wait for the DMR API to answer before the tests probe it
for i in $(seq 1 30); do
    if curl -sf http://localhost:12434/models >/dev/null 2>&1; then
        echo "Docker Model Runner is up"
        break
    fi
    echo "Waiting for Docker Model Runner... ($i/30)"
    sleep 2
done

#Pre-pull the small model the IT tests use, so Delete and Configure have it available
docker model pull ai/smollm2