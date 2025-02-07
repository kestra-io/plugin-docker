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
docker pull ubuntu
docker login localhost:5000 -u testuser -p testpassword
docker tag ubuntu localhost:5000/ubuntu:unit-test
docker push localhost:5000/ubuntu:unit-test

docker logout localhost:5000