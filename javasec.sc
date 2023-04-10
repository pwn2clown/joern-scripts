import io.shiftleft.semanticcpg.language._


@main def exec(cpgPath: String) = {
  importCpg(cpgPath)

  val spring_annotation = Set(
      "RequestMapping",
      "GetMapping",
      "PostMapping",
      "PutMapping",
      "PatchMapping",
      "DeleteMapping"
    )

  //  - Spring user input like:
  //
  //    @RequestMapping("/jdbc/sec")
  //    public String jdbc_sqli_sec(@RequestParam("username") String username) {
  
  def sources = cpg.annotation
    .where(_.name.within(spring_annotation))
    .method.parameter.orderGte(1)

  //  - SQLI: queryForMap injection 
  //  
  //  	String query = "SELECT * FROM users WHERE USERNAME=\"" + username + "\" AND PASSWORD=\"" + password + "\"";
  //	Map<String, Object> result = jdbcTemplate.queryForMap(query);

  def queryForMap_args = cpg.call
    .methodFullName("org.springframework.jdbc.core.JdbcTemplate.queryForMap:.*")
    .argument.argumentIndex(1)
  
  var results = queryForMap_args
    .reachableByFlows(sources)
    .filter(
        //  Ignore operations on jdbcTemplate like setting parameters as we only want to know
        //  if the statement argument is reachable from user input.
        _.elements.isCall.typeFullName("org.springframework.jdbc.core.JdbcTemplate").isEmpty
      )
    .p
 
  println(results)

  //  - SQLI: prepareStatement injection (case 1)
  //
  //    String sql = "select * from users where username = '" + username + "'";
  //    PreparedStatement st = con.prepareStatement(sql);
  //    ResultSet rs = st.executeQuery();

  def stmt_builder = cpg.call("prepareStatement")
    .where(_.argument.reachableBy(sources))
  
  results = cpg.call("executeQuery")
    .where(_.argument.reachableBy(stmt_builder))
    .reachableByFlows(sources).p

  println(results)

  //  TODO: cover cases like:
  //  ResultSet rs = statement.executeQuery(sql)
  
  //  - RCE: Runtime.exec
  //
  //    Runtime run = Runtime.getRuntime();
  //    Process p = run.exec(cmd);

  results = cpg.call
    .methodFullName("java.lang.Runtime.exec.*")
    .reachableByFlows(sources).p

  println(results)

  //  - RCE: ProcessBuilder.start
  //
  //    String[] arrCmd = {"/bin/sh", "-c", cmd};
  //    ProcessBuilder processBuilder = new ProcessBuilder(arrCmd);
  //    Process p = processBuilder.start();

  results = cpg.call
    .methodFullName("java.lang.ProcessBuilder.start:java.lang.Process\\(\\)")
    .reachableByFlows(sources).p

  println(results)

  //  - Arbitrary redirection: Location header injection
  //
  //    String url = request.getParameter("url");
  //    response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY); // 301 redirect
  //    response.setHeader("Location", url);

  results = cpg.call
    .methodFullName(".*setHeader.*")
    .where(_.argument.argumentIndex(1).code("\"Location\""))
    .reachableByFlows(sources).p

  println(results)
}
