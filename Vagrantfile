Vagrant.configure(2) do |config|
   config.vm.define "testvm" do |testvm|
      testvm.vm.box = "http://develop.dans.knaw.nl/boxes/centos-6.8-2016-09-07.box"
      testvm.vm.hostname = "testvm"
      testvm.vm.network :private_network, ip: "192.168.33.32"
      testvm.vm.network "forwarded_port", guest: 80, host: 8080
      testvm.vm.provision "ansible" do |ansible|
      ansible.playbook = "src/main/ansible/vagrant.yml"
       ansible.config_file = "src/main/ansible/ansible.cfg"
#        ansible.verbose = "vvvv"
      end
#      testvm.vm.synced_folder "data",  "/vagrant"
      testvm.vm.provider "virtualbox" do |vb|
        vb.gui = false
        vb.memory = 2072
        vb.cpus = 2
        vb.customize ["guestproperty", "set", :id, "--timesync-threshold", "1000"]
        vb.customize ["guestproperty", "set", :id, "--timesync-interval", "1000"]
      end
   end
end
