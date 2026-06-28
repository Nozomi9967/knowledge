

## docker-compose.yml



```yaml
version: '3.8'
services:
  elasticsearch:
    restart: unless-stopped
    build: .
    #image: elasticsearch:8.12.2
    container_name: elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ulimits:
      memlock:
        soft: -1
        hard: -1
    ports:
      - 9200:9200
    volumes:
      - es-data:/usr/share/elasticsearch/data
    networks:
      - elastic

  kibana:
    restart: unless-stopped
    image: kibana:8.12.2
    container_name: kibana
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
      - I18N_LOCALE=zh-CN
      - XPACK_SECURITY_ENABLED=false
    ports:
      - 5601:5601
    depends_on:
      - elasticsearch
    networks:
      - elastic

networks:
  elastic:
    driver: bridge

volumes:
  es-data:
    driver: local

```





## DockerFile

```
FROM elasticsearch:8.12.2
RUN bin/elasticsearch-plugin install -b https://get.infini.cloud/elasticsearch/analysis-ik/8.12.2

```



## 使用



### 访问kibana

http://localhost:5601