Pre release
- [ ] pull and rebase changes from remote `git pull --rebase origin master`
- [ ] `mvn -B package` and `mvn -B test` pass with JDK 21
- [ ] Change log has been updated
- [ ] Added/updated documentation for new properties/features
- [ ] The version in `pom.xml` is the release version
- [ ] ReadMe file or docs are updated for any change, user list, performance report, links etc.
- [ ] No test is disabled

Release: ensure not uncommited changes
- Create an annotated git tag on the release commit, named after the version (e.g. `v5.11.2`)
- run `git push origin master --tags`

In general
- [ ] tests are added/updated

Post release
- [ ] Notified to the users
- [ ] Sync master and dev branches

To remove tag
git tag -d <tag_name>
