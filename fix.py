import os
import glob

# 1. Fix invoice-service/pom.xml
pom_path = 'invoice-service/pom.xml'
with open(pom_path, 'r') as f:
    pom = f.read()
if '<artifactId>micrometer-registry-prometheus</artifactId>' not in pom:
    pom = pom.replace(
        '<artifactId>spring-boot-starter-actuator</artifactId>\n        </dependency>',
        '<artifactId>spring-boot-starter-actuator</artifactId>\n        </dependency>\n        <dependency>\n            <groupId>io.micrometer</groupId>\n            <artifactId>micrometer-registry-prometheus</artifactId>\n        </dependency>'
    )
    with open(pom_path, 'w') as f:
        f.write(pom)

# 2. Fix invoice-service/src/main/resources/application.yml
yml_path = 'invoice-service/src/main/resources/application.yml'
with open(yml_path, 'r') as f:
    yml = f.read()
if 'prometheus,health,info' not in yml:
    yml += "\nmanagement:\n  endpoints:\n    web:\n      exposure:\n        include: \"prometheus,health,info\"\n  metrics:\n    distribution:\n      percentiles-histogram:\n        http.server.requests: true\n"
    with open(yml_path, 'w') as f:
        f.write(yml)

# 3. Fix all JSON and YAML in k8s/monitoring/grafana/
for ext in ('*.json', '*.yaml'):
    for path in glob.glob(f'k8s/monitoring/grafana/**/{ext}', recursive=True):
        with open(path, 'r') as f:
            content = f.read()
        
        # LogQL Fixes
        content = content.replace('{app=\\"user-service\\"}', '{service=\\"user-service\\"}')
        content = content.replace('{app=\\"provider-service\\"}', '{service=\\"provider-service\\"}')
        content = content.replace('{app=\\"booking-service\\"}', '{service=\\"booking-service\\"}')
        content = content.replace('{app=\\"calendar-service\\"}', '{service=\\"calendar-service\\"}')
        content = content.replace('{app=\\"invoice-service\\"}', '{service=\\"invoice-service\\"}')

        # PromQL Fixes (HikariCP application -> job)
        content = content.replace('{application=\\"user-service\\"}', '{job=\\"user-service\\"}')
        content = content.replace('{application=\\"provider-service\\"}', '{job=\\"provider-service\\"}')
        content = content.replace('{application=\\"booking-service\\"}', '{job=\\"booking-service\\"}')
        content = content.replace('{application=\\"calendar-service\\"}', '{job=\\"calendar-service\\"}')
        content = content.replace('{application=\\"invoice-service\\"}', '{job=\\"invoice-service\\"}')

        with open(path, 'w') as f:
            f.write(content)
