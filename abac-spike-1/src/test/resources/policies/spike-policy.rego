package abac_spike

default allow_partial = false

allow_partial {
  input.action == "GET"
  data.accountState.broker.id == input.brokerId
}