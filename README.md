
opencode run -m opencode/big-pickle --agent backend-developer   "Review the recent code changes and provide feedback on:
  - Code quality and readability
  - Possible bugs or issues
  - Security considerations
  - Best-practices compliance

  Provide specific improvement suggestions."



opencode run -m opencode/big-pickle --agent backend-developer   "Review (using skill /adversarial-review) the recent code changes and provide feedback on:
  - Code quality and readability
  - Possible bugs or issues
  - Security considerations
  - Best-practices compliance

  Provide specific improvement suggestions."
  
  
  
  
http://localhost:9000/api/issues/search?components=ai.sergio%3Aopenspec-demo&s=FILE_LINE&issueStatuses=CONFIRMED%2COPEN&ps=100&facets=impactSoftwareQualities%2Cseverities%2Ctypes%2CimpactSeverities%2CcodeVariants&additionalFields=_all&timeZone=America%2FBuenos_Aires

http://localhost:9000/api/issues/search?components=ai.sergio%3Aopenspec-demo&s=FILE_LINE&issueStatuses=CONFIRMED%2COPEN&ps=100&facets=impactSoftwareQualities%2Cseverities%2Ctypes%2CimpactSeverities%2CcodeVariants&additionalFields=_all&timeZone=America%2FBuenos_Aires

curl -s -u admin:Admin123456! "http://localhost:9000/api/issues/search?components=ai.sergio%3Aopenspec-demo&s=FILE_LINE&issueStatuses=CONFIRMED%2COPEN&ps=100&facets=impactSoftwareQualities%2Cseverities%2Ctypes%2CimpactSeverities%2CcodeVariants&additionalFields=_all"