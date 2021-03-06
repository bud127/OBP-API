package code.api

import code.api.util.ErrorMessages
import code.bankconnectors.vJune2017.InboundAccountJune2017
import code.bankconnectors.vMar2017.InboundStatusMessage
import code.setup.{APIResponse, DefaultUsers, ServerSetup}
import net.liftweb.common.Full
import net.liftweb.json
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.{JField, JObject, JString}
import net.liftweb.util.Props
import org.scalatest._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetAnyUser
import code.api.util.ErrorMessages.UserHasMissingRoles

class gateWayloginTest extends ServerSetup with BeforeAndAfter with DefaultUsers {

  //fake this: Connector.connector.vend.getBankAccounts(username)
  val fakeResultFromAdapter =  Full(InboundAccountJune2017(
    errorCode = "",
    List(InboundStatusMessage("ESB", "Success", "0", "OK")),
    cbsToken ="cbsToken1",
    bankId = "gh.29.uk",
    branchId = "222",
    accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
    accountNumber = "123",
    accountType = "AC",
    balanceAmount = "50",
    balanceCurrency = "EUR",
    owners = "Susan" :: " Frank" :: Nil,
    viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil,
    bankRoutingScheme = "iban",
    bankRoutingAddress = "bankRoutingAddress",
    branchRoutingScheme = "branchRoutingScheme",
    branchRoutingAddress = " branchRoutingAddress",
    accountRoutingScheme = "accountRoutingScheme",
    accountRoutingAddress = "accountRoutingAddress"
  ) :: InboundAccountJune2017(
    errorCode = "",
    List(InboundStatusMessage("ESB", "Success", "0", "OK")),
    cbsToken ="cbsToken2",
    bankId = "gh.29.uk",
    branchId = "222",
    accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
    accountNumber = "123",
    accountType = "AC",
    balanceAmount = "50",
    balanceCurrency = "EUR",
    owners = "Susan" :: " Frank" :: Nil,
    viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil,
    bankRoutingScheme = "iban",
    bankRoutingAddress = "bankRoutingAddress",
    branchRoutingScheme = "branchRoutingScheme",
    branchRoutingAddress = " branchRoutingAddress",
    accountRoutingScheme = "accountRoutingScheme",
    accountRoutingAddress = "accountRoutingAddress"
  ) ::Nil)


  val accessControlOriginHeader = ("Access-Control-Allow-Origin", "*")

  val invalidSecretJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpYXQiOjE0MTY5MjkxMDksImp0aSI6ImFhN2Y4ZDBhOTVjIiwic2NvcGVzIjpbInJlcG8iLCJwdWJsaWNfcmVwbyJdfQ.XCEwpBGvOLma4TCoh36FU7XhUbcskygS81HE1uHLf0E"
  val jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6InNpbW9uciIsImlzX2ZpcnN0IjpmYWxzZSwiQ0JTX2F1dGhfdG9rZW4iOiJxZndxZXZ3cmJ3dmIiLCJ0aW1lc3RhbXAiOiJ0aW1lc3RhbXAiLCJjb25zdW1lcl9pZCI6IjEyMyIsImNvbnN1bWVyX25hbWUiOiJOYW1lIG9mIENvbnN1bWVyIn0.Ztu_J0WpufqsN6LlOtpKppEgZwGTpZVu7TSMLVY6vr4"

  val invalidJwt = ("Authorization", ("GatewayLogin token=%s").format(invalidSecretJwt))
  val validJwt = ("Authorization", ("GatewayLogin token=%s").format(jwt))
  val missingParameterToken = ("Authorization", ("GatewayLogin wrong_parameter_name=%s").format(jwt))

  def gatewayLoginRequest = baseRequest / "obp" / "v3.0.0" / "users"

  feature("GatewayLogin") {
    Props.getBool("allow_gateway_login", false) match  {
      case true =>
        scenario("Missing parameter token") {
          When("We try to login without parameter token in a Header")
          val request = gatewayLoginRequest
          val response = makeGetRequest(request, List(missingParameterToken))
          Then("We should get a 400 - Bad Request")
          response.code should equal(400)
          assertResponse(response, ErrorMessages.GatewayLoginMissingParameters + "token")
        }

        scenario("Invalid JWT value") {
          When("We try to login with an invalid JWT")
          val request = gatewayLoginRequest
          val response = makeGetRequest(request, List(invalidJwt))
          Then("We should get a 400 - Bad Request")
          response.code should equal(400)
          assertResponse(response, ErrorMessages.GatewayLoginJwtTokenIsNotValid)
        }

        scenario("Valid JWT value") {
          When("We try to login with an valid JWT")
          val request = gatewayLoginRequest.GET <@ (userGatewayLogin)
          val response = makeGetRequest(request, List(validJwt))
          Then("We should get a 400 - Bad Request because we miss a proper role")
          response.code should equal(400)
          assertResponse(response, UserHasMissingRoles + CanGetAnyUser)
        }
      case false =>
        logger.info("-----------------------------------------------------------------")
        logger.info("------------- GatewayLogin Test is DISABLED ---------------------")
        logger.info("-----------------------------------------------------------------")
    }
  }


  feature("Unit Tests for two getCbsToken and getErrors: ") {
    scenario("test the getErrors") {
      val reply: List[String] =  GatewayLogin.getErrors(json.compactRender(Extraction.decompose(fakeResultFromAdapter.openOrThrowException("Attempted to open an empty Box."))))
      reply.forall(_.equalsIgnoreCase("")) should equal(true)
    }

    scenario("test the getCbsToken") {
      val reply: List[String] =  GatewayLogin.getCbsTokens(json.compactRender(Extraction.decompose(fakeResultFromAdapter.openOrThrowException("Attempted to open an empty Box."))))
      reply(0) should equal("cbsToken1")
      reply(1) should equal("cbsToken2")

      reply.exists(_.equalsIgnoreCase("")==false) should equal(true)
    }
  }



  private def assertResponse(response: APIResponse, expectedErrorMessage: String): Unit = {
    response.body match {
      case JObject(List(JField(name, JString(value)))) =>
        name should equal("error")
        value should startWith(expectedErrorMessage)
      case _ => fail("Expected an error message")
    }
  }
}