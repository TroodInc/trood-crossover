from custodian.client import Client

try:
    client = Client(server_url="http://stage.trood.ru/custodian/")

    BaseOrder = client.objects.get('base_order')
    Employee = client.objects.get('employee')
    Payment = client.objects.get('payment')
    Contractor = client.objects.get('contractor')
    ContactPerson = client.objects.get('contact_person')
    Contact = client.objects.get('contact')

except:
    raise
    BaseOrder = None
    Employee = None
    Payment = None
    Contractor = None
    ContactPerson = None