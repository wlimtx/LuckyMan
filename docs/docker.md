### 用socat容器代理docker宿主容器端口

`docker run -d -v /var/run/docker.sock:/var/run/docker.sock -p 192.168.2.175:2375:2375 bobrik/socat TCP-LISTEN:2375,fork UNIX-CONNECT:/var/run/docker.sock`

docker -H 127.0.0.1:2375 info

### 启动docker container dashboard
docker run -d -p 9000:9000 -v /var/run/docker.sock:/var/run/docker.sock portainer/portainer




### 每次重启operator-dashboard服务后应该执行的命令
`docker exec cello-operator-dashboard bash -c 'cd agent/docker/;sed "s/.opt.cello/\/Users\/liumingxing\/work\/Docker\/opt\/cello/1" docker_swarm.py > .temp.py;rm docker_swarm.py;mv .temp.py docker_swarm.py;'`

修改默认的fabric启动路径
grep ".opt.cello" docker_swarm.py
