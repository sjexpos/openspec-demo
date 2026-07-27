#!/usr/bin/env bash

echo "Waiting SonarQube is started"
echo "http://sonarqube:9000/api/system/status"
while true; do 
  status=$(curl -s "http://sonarqube:9000/api/system/status" | jq -r '.status') 
  if [ "$status" == "UP" ]; then 
    echo "Service is up and running." 
    break 
  fi 
  echo "Waiting..." 
  sleep 5 
done

echo "Change default password"
curl -s -u $SONAR_USER:$SONAR_PASSWORD -X POST "http://sonarqube:9000/api/users/change_password?login=$SONAR_USER&previousPassword=$SONAR_PASSWORD&password=$SONAR_NEW_PWD"

sleep 5 

echo "Create token"
sonar_token=$(curl -s -u $SONAR_USER:$SONAR_NEW_PWD http://sonarqube:9000/api/user_tokens/generate -d 'name=token' | jq -r '.token')
echo "-------------"
echo "$sonar_token"
echo "-------------"
