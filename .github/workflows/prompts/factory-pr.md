Read the status of jira ticket {{JIRA_TICKET}}.
If the ticket status is "IN PROGRESS", create a pull request from current changes (which MUST be composed by only one commit), add a brief description about the changes, and when the PR is successfully created, move jira ticket to "IN REVIEW" status.
If the ticket status is not "IN PROGRESS", push current changes to the current feature branch (which MUST be composed by only one commit).
